package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.function.Consumer;
import java.util.Queue;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.consensus.Istanbul;
import pt.ulisboa.tecnico.hdsledger.consensus.Timer;
import pt.ulisboa.tecnico.hdsledger.consensus.SimpleTimer;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;

import com.google.gson.Gson;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // Nodes configurations
    private final List<ProcessConfig> others;

    // My configuration
    private final ProcessConfig config;
    
    // Link to communicate with nodes
    private final Link link;

    // Ledger (for now, just a list of strings)
    // TODO (dsa): factor out to a state class
    private ArrayList<String> ledger = new ArrayList<String>();

    // We'll allow multiple instances to run in parallel if needed, so this
    // map needs to be thread-safe
    // Maps: lambda -> instance
    private Map<Integer, Istanbul> instances = new ConcurrentHashMap<>();

    // Callback to call when a new input is finalized by consensus (only if
    // the input was provided by this replica)
    private Queue<Consumer<Slot>> observers = new ConcurrentLinkedQueue<>();

    // Blocking queue of pending inputs (thread-safe)
    private BlockingQueue<String> inputs = new LinkedBlockingQueue<>();

    // Blocking queue of decisions (thread-safe)
    private BlockingQueue<Decision> decisions = new LinkedBlockingQueue<>(); 

    // Whether service is running
    private AtomicBoolean running = new AtomicBoolean(false);

    // Whether listen has been called
    private AtomicBoolean listening = new AtomicBoolean(false);

    // Current consensus instance
    private AtomicInteger currentLambda = new AtomicInteger(0);

    // Stashed messages for future rounds (does not need to be thread-safe, because
    // it'll have synchronization around it)
    private Map<Integer, List<ConsensusMessage>> stashed = new HashMap<>();

    // Running threads (warning: can't remove listening property before of 
    // thread safety, which is not provided by this thread's property)
    private Optional<List<Thread>> threads = Optional.empty();

    // List of paths to client public keys
    private List<String> clientPks;

    // maps values to the slot they were finalized to (after
    // coming out from consensus)
    Map<String, Slot> history = new ConcurrentHashMap<>();

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig[] nodesConfig, List<String> clientPks) {
        this.link = link;
        this.config = config;
        this.others = Arrays.asList(nodesConfig);
        this.clientPks = clientPks;
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public ArrayList<String> getLedger() {
        return this.ledger;
    }

    /**
     * Check that value is valid
     */
    private boolean checkIsValidValue(int lambda, String value) {
        Optional<List<String>> parts = parseValue(value);
        if (!parts.isPresent()) {
            return false;
        }

        int clientId = Integer.parseInt(parts.get().get(0));
        int seq = Integer.parseInt(parts.get().get(1));
        String cmd = parts.get().get(2);
        String serializedProof = parts.get().get(3);

        int n = this.others.size();
        if (clientId < n) {
            LOGGER.log(Level.WARNING, MessageFormat.format("{0} - signature check for append was requested for client with ID smaller than n, which is wrong (value is {1})",
                    config.getId(), value));
        }

        LOGGER.log(Level.WARNING, MessageFormat.format("{0} - signature check for client {2}, message {1} starting",
                config.getId(), value, clientId));

        // Check it wasn't proposed yet
        boolean repeated = history.entrySet()
            .stream()
            .map(e -> e.getValue())
            .anyMatch(s -> s.getSeq() == seq && s.getClientId() == clientId &&
                s.getMessage().equals(cmd));
        if (repeated) {
            return false;
        }

        // Check client signature is valid
        AppendMessage hmacMessage = new Gson().fromJson(serializedProof, AppendMessage.class);

        LOGGER.log(Level.WARNING, MessageFormat.format("{0} - signature check for client {1} - getting key at position {2}",
                config.getId(), clientId, clientId-n));
        return hmacMessage.checkConsistentSig(this.clientPks.get(clientId - n));
    }

    /**
     * Returns instance with id lambda and creates one if it doesn't exist
     */
    private Istanbul getInstance(int lambda) {
        // TODO (dsa): check that lambda <= current lambda
        // if lambda > current lambda (or lambda > current lambda + 1 ?), then
        // i can't yet decide on the beta predicate to provide (because it depends
        // on the proposals so far)
        // beta should check that no value so far is the same as the current
        // one and that the signature is correct
 
        return instances.computeIfAbsent(lambda, l -> {
            Istanbul instance = new Istanbul(this.others, this.config, l, value -> this.checkIsValidValue(l, value));
            Timer timer = new SimpleTimer();
            Consumer<String> observer = s -> {
                decided(l, s);
            };
    
            instance.registerObserver(observer);
            instance.setTimer(timer);
            return instance;
        });
    }

    /*
     * Starts a new consensus instance.
     * Does not check that there's not other ongoing instances.
     * Does not check that it's the current instance.
     * Thread-safe.
     *
     * @param lambda
     */
    private void actuallyInput(int lambda, String value) {
        Istanbul instance = getInstance(lambda);

        // Input into state machine
        List<ConsensusMessage> output = instance.start(value);

        // Dispatch messages
        output.forEach(m -> link.send(m.getReceiver(), m));

        // Dispatch all stashed message for round
        List<ConsensusMessage> messages;

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Actually starting instance {1} with {2} - getting stashed messages",
                    config.getId(), lambda, value));
        synchronized (stashed) {
            messages = stashed.remove(lambda);
        }

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Actually starting instance {1} with {2} - starting to parse stashed messages",
                    config.getId(), lambda, value));

        // Handle all stashed messages (if there were stashed messages)
        if (messages != null) {
            messages.forEach(message -> this.handleMessage(message));
        }
    }

    /*
     * Start an instance of consensus for a cmd if one is not ongoing, otherwise
     * just queues input to be eventually added to state.
     * Thread-safe.
     *
     * @param cmd value to append to state
     * @param nonce should be unique and not contain `::`
     * @param proof message proving that client did submit the transaction to be appended
     */
    public synchronized void startConsensus(int clientId, int seq, String cmd, AppendMessage proof) {
        String serializedProof = new Gson().toJson(proof);
        String value = String.format("%s::%s::%s::%s", clientId, seq, cmd, serializedProof);

        // note: add must be used instead of put as it's non-blocking
        inputs.add(value); 
    }

    /**
     * Handle consensus message 
     * @param message Consensus message
     * Thread-safe (but not mutual exclusion region)
     */
    private void handleMessage(ConsensusMessage message) {
        int lambda = message.getConsensusInstance();
        Istanbul instance = getInstance(lambda);

        // Input into state machine
        List<ConsensusMessage> output = instance.handleMessage(message); 
        
        // Dispatch messages
        output.stream().forEach(m -> link.send(m.getReceiver(), m));
    }

    /**
     * Handle decision for instance lambda
     * Just register decision
     * Thread-safe.
     */
    private void decided(int lambda, String value) {

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Decided on Consensus Instance {1} with value {2}",
                        config.getId(), lambda, value));

        Decision d = new Decision(lambda, value);
        decisions.add(d);
    }

    /**
     * Registers observer for confirmation of finalization of inputted values
     * @param observer Callback function
     * Thread-safe.
     */
    public void registerObserver(Consumer<Slot> observer) {
        this.observers.add(observer);
    }

    /*
     * Takes string of form nonce::command and returns and Option with [nonce; command]
     * if it's in valid format, otherwise returns empty option.
     * */
    public static Optional<List<String>> parseValue(String value) {
        // Value is always of the form nonce::m if is proposed by correct process
        // if not, then it's considered invalid by all valid nodes and discarded
        
        String[] parts = value.split("::");
        if (parts.length != 4) {
            return Optional.empty();
        }

        return Optional.of(Arrays.asList(parts));
    }

    /*
     * Takes string of form nonce::command and returns and Option with [nonce; command]
     * if it's in valid format, otherwise returns empty option.
     * */
    public static Optional<List<String>> parseStrippedValue(String value) {
        // Value is always of the form nonce::m if is proposed by correct process
        // if not, then it's considered invalid by all valid nodes and discarded
        
        String[] parts = value.split("::");
        if (parts.length != 3) {
            return Optional.empty();
        }

        return Optional.of(Arrays.asList(parts));
    }

    public static Optional<String> stripProof(String value) {
        Optional<List<String>> partsOpt = parseValue(value);

        if (!partsOpt.isPresent()) {
            return Optional.empty();
        }

        List<String> parts = partsOpt.get();

        String stripped = String.format("%s::%s::%s", parts.get(0), parts.get(1), parts.get(2));

        return Optional.of(stripped);
    }

    /**
     * Updates state and returns slot position for value
     * Non thread-safe.
     */
    public int updateState(String value) {
        ledger.add(value);
        LOGGER.log(Level.INFO,
                MessageFormat.format(
                    "{0} - Current Ledger: {1}",
                    config.getId(), String.join("|", ledger)));

        return ledger.size();
    }

    @Override
    public void listen() {
        if (this.listening.getAndSet(true)) {
            throw new RuntimeException("NodeService.listen already called for this service instance");
        }

        this.running.set(true);

        List<Thread> threads = new ArrayList<>();
        try {
            // Thread to listen on every request
            Thread networkListener = new Thread(() -> {
                try {
                    LOGGER.log(Level.FINER, MessageFormat.format("{0} Message listener - Setting up workers thread pool",
                        config.getId()));

                    ExecutorService pool = Executors.newCachedThreadPool();

                    while (this.running.get()) {
                        LOGGER.log(Level.FINER, MessageFormat.format("{0} Message listener - Trying to get new message",
                                config.getId()));

                        Message message = link.receive();

                        LOGGER.log(Level.FINER, MessageFormat.format("{0} Message listener - Got new message",
                                config.getId()));

                        // If all upon rules are synchronized, there's no point
                        // in creating a new thread to handle each message
                        switch (message.getType()) {

                            case PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE -> {
                                ConsensusMessage comessage = (ConsensusMessage) message;
                                int lambda = comessage.getConsensusInstance();

                                // Only handle message for which we can have a predicate (i.e.
                                // for previous rounds)
                                // Need to synchronize, otherwise after round check and
                                // before adding to stashed and messages are effectively
                                // lost

                                LOGGER.log(Level.FINER, MessageFormat.format("{0} Message listener - Entering stashed lock section",
                                        config.getId()));

                                synchronized (stashed) {
                                    int myLambda = this.currentLambda.get();

                                    // Stash future messages for later processing
                                    if (lambda > myLambda) {
                                        LOGGER.log(Level.INFO, MessageFormat.format("{0} Message listener - Message for future instance {1} while Im at {2}",
                                                    config.getId(), lambda, myLambda));

                                        this.stashed.putIfAbsent(lambda, new ArrayList<>());
                                        this.stashed.get(lambda).add(comessage);

                                        LOGGER.log(Level.INFO, MessageFormat.format("{0} Message listener - Stashed message from future instance {1} while Im at {2}",
                                                    config.getId(), lambda, myLambda));

                                        continue;
                                    }
                                }

                                pool.execute(() -> handleMessage(comessage));
                            }

                            case ACK ->
                                LOGGER.log(Level.INFO, MessageFormat.format("{0} Message listener - Received ACK message from {1}",
                                        config.getId(), message.getSenderId()));

                            case IGNORE ->
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} Message listener - Received IGNORE message from {1}",
                                                config.getId(), message.getSenderId()));

                            default ->
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} (NodeService) Message listener - Received unknown message from {1}",
                                                config.getId(), message.getSenderId()));
                        }
                    }

                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            });

            // Thread to take pending inputs from clients and input them into consensus
            Thread driver = new Thread(() -> {
                try {
                    DecisionBucket bucket = new DecisionBucket();


                    // input values for which the observers where already notified
                    Set<String> acketToObserver = new HashSet();

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} Driver - Waiting for input",
                                config.getId()));
                    // take must be used instead of remove because it's blocking.
                    // need to do get input outside loop to bootstrap.
                    String input = this.inputs.take();
                    String strippedInput = stripProof(input).get();

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} Driver - Got input {1}",
                                config.getId(), input));


                    while (this.running.get()) {
                        // if input was already processed after agreement, there's
                        // nothing to do besides getting a new value
                        // TODO (dsa): probably no longer need this (because beta
                        // ensures that accept value was not already decided and is valid)
                        
                        // Input contains the proof, which might differ from mine
                        // so this should be checked with input without last part
                        if (history.containsKey(strippedInput)) {

                            // if not notified observers, notify
                            // (it's possible for a value to be processed, but
                            // the clients not notified if the input came late)
                            if (!acketToObserver.contains(strippedInput)) {
                                Slot slot = history.get(strippedInput);
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} Driver - Confirming {1} to client for slot in position {2}",
                                            config.getId(), input, slot.getSlotId()));
                                for (Consumer<Slot> obs: this.observers) {
                                    obs.accept(slot);
                                }
                                acketToObserver.add(strippedInput);
                            }

                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Waiting for input",
                                        config.getId()));
                            // take must be used instead of remove because it's blocking.
                            input = this.inputs.take(); // blocking
                            strippedInput = stripProof(input).get();

                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Got input {1}",
                                        config.getId(), input));

                            continue;
                        }

                        // start new instance (can't be after take, because 
                        // take's value might already be finalized as well)
                        currentLambda.incrementAndGet();
                        LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} Driver - Inputting into consensus {1} value {2}",
                                    config.getId(), currentLambda.get(), input));
                        actuallyInput(currentLambda.get(), input);

                        LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} Driver - Inputted, now I wait for decision",
                                    config.getId()));

                        // check if this instance was already decided upon. if it
                        // was, then no need to wait, otherwise wait for more
                        // decisions
                        Decision d;
                        while (!bucket.contains(currentLambda.get())) {
                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Decision for instance {1} has not yet been reached",
                                        config.getId(), currentLambda.get()));

                            // take must be used instead of remove because it's blocking
                            d = decisions.take();
                            bucket.save(d);

                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Decision for instance {1} was reached",
                                        config.getId(), d.getLambda()));

                        }

                        LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} Driver - Finally decision for my instance ({1}) was reached",
                                    config.getId(), currentLambda.get()));
                        d = bucket.get(currentLambda.get());


                        // at this point, we have decision for instance currentLambda,
                        // we just need to process it


                        // if this consensus output is duplicate, there's nothing
                        // to be done
                        String value = d.getValue();
                        String strippedValue = stripProof(value).get();
                        if (history.containsKey(strippedValue)) {
                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - History already contained ({1}), skipping for this reason",
                                        config.getId(), d.getValue()));

                            continue;
                        }

                        // if it's not duplicate, parse
                        Optional<List<String>> parts = parseValue(value);
                        if (parts.isPresent()) {
                            int clientId = Integer.parseInt(parts.get().get(0));
                            int seq = Integer.parseInt(parts.get().get(1));
                            String cmd = parts.get().get(2);
                            // TODO: save proofs (might be needed for compliance for example)
                            String serializedProof = parts.get().get(3);
                            
                            // update state
                            int slotId = updateState(cmd);
                            Slot slot = new Slot(slotId, seq, clientId, cmd);
                            history.put(strippedValue, slot);
                        } else {
                            // TODO (dsa): raise exception, because this should not happen
                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Bad value format (should be nonce::cmd), discarding",
                                        config.getId(), d.getValue()));
                        }
                    }


                } catch (InterruptedException e) {
                    // interrupt is used to stop thread
                } finally {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} Driver - finished",
                                config.getId()));
                }

            });

            networkListener.start();
            driver.start();

            threads.add(networkListener);
            threads.add(driver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.threads = Optional.of(threads);
    }

    @Override
    public void stopAndWait() {
        // FIXME (dsa): it's a bit cursed to have the local variable have
        // the same name as the property
        
        if (!listening.get() || !this.threads.isPresent()) {
            return;
        }

        List<Thread> threads = this.threads.get();
        this.running.set(false);
        for (Thread t: threads) {
            try {
                t.interrupt();
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Represents a consensus instance decision
     */
    private static class Decision {
        private int lambda;
        private String value;

        Decision(int lambda, String value) {
            this.lambda = lambda;
            this.value = value;
        }

        int getLambda() {
            return this.lambda;
        }

        String getValue() {
            return this.value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Decision decision = (Decision) o;
            return lambda == decision.lambda &&
                value.equals(decision.getValue());
        }

        @Override
        public int hashCode() {
            return value.hashCode() + lambda;
        }
    }

    /**
     * Bucket where decisions are stored (not thread-safe)
     */
    private static class DecisionBucket {
        private Map<Integer, Decision> bucket = new HashMap<>();

        DecisionBucket() { }

        /**
         * Stores decision d in bucket
         */
        void save(Decision d) {
            bucket.put(d.getLambda(), d);
        }

        /**
         * Returns whether there's a decision for instance lambda
         */
        boolean contains(int lambda) {
            return bucket.containsKey(lambda);
        }

        /**
         * Returns instance lambda's decision
         */
        Decision get(int lambda) {
            return bucket.get(lambda);
        }
    }

    // Mostly for testing purposes
    public int getId() {
        return this.config.getId();
    }
}
