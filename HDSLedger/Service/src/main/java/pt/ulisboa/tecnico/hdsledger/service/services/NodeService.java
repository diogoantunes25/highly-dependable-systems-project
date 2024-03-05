package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
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

import pt.ulisboa.tecnico.hdsledger.consensus.message.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.consensus.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.consensus.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.consensus.Instanbul;
import pt.ulisboa.tecnico.hdsledger.consensus.Timer;
import pt.ulisboa.tecnico.hdsledger.consensus.SimpleTimer;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.service.Slot;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // Nodes configurations
    private final ProcessConfig[] nodesConfig;

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
    private Map<Integer, Instanbul> instances = new ConcurrentHashMap<>();

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
    // thread safety, which is not provided by this threads property)
    private Optional<List<Thread>> threads = Optional.empty();

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig[] nodesConfig) {
        this.link = link;
        this.config = config;
        this.nodesConfig = nodesConfig;
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public ArrayList<String> getLedger() {
        return this.ledger;
    }

    /**
     * Check that mesage is valid
     */
    private boolean checkIsValidMessage(String value) {
        // TODO (dsa)
        return true;
    }

    /**
     * Returns instance with id lambda and creates one if it doesn't exist
     */
    private Instanbul getInstance(int lambda) {
        // TODO (dsa): check that lambda <= current lambda
        // if lambda > current lambda (or lambda > current lambda + 1 ?), then
        // i can't yet decide on the beta predicate to provide (because it depends
        // on the proposals so far)
        // beta should check that no value so far is the same as the current
        // one and that the signature is correct
 
        return instances.computeIfAbsent(lambda, l -> {
            Instanbul instance = new Instanbul(this.config, l, value -> this.checkIsValidMessage(value));
            // TODO (dsa): probably don't need a timer per instance (one for all is enough)
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
        Instanbul instance = getInstance(lambda);

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
     * just queues input to be evetually added to state.
     * Thread-safe.
     *
     * @param cmd value to append to state
     * @param nonce should be unique and not contain `::`
     */
    public synchronized void startConsensus(String nonce, String cmd) {
        // note: add must be used instead of put as it's non-blocking
        String value = String.format("%s::%s", nonce, cmd);
        inputs.add(value); 
    }

    /**
     * Handle consensus message 
     * @param message Consensus message
     * Thread-safe.
     */
    private void handleMessage(ConsensusMessage message) {
        // TODO: synchronize in a per instance basis

        int lambda = message.getConsensusInstance();
        Instanbul instance = getInstance(lambda);

        // Input into state machine
        List<ConsensusMessage> output = instance.handleMessage(message); 

        // Dispatch messages
        output.forEach(m -> link.send(m.getReceiver(), m));
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
        if (parts.length != 2) {
            return Optional.empty();
        }

        return Optional.of(Arrays.asList(parts));
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

                                // Only handle message for which we can have a predicate (i.e
                                // for previous rounds)
                                // Need to synchronize, otherwise after round check and
                                // before adding to stashed and messages are effectively
                                // lost

                                LOGGER.log(Level.FINER, MessageFormat.format("{0} Message listener - Entering stashed lock section",
                                        config.getId()));

                                synchronized (stashed) {
                                    int myLambda = this.currentLambda.get();
                                    if (lambda != myLambda) {

                                        // Stash future messages for later processing
                                        if (lambda > myLambda) {
                                            LOGGER.log(Level.INFO, MessageFormat.format("{0} Message listener - Message for future instance {1} while Im at {2}",
                                                    config.getId(), lambda, myLambda));

                                            this.stashed.putIfAbsent(lambda, new ArrayList<>());
                                            this.stashed.get(lambda).add(comessage);

                                            LOGGER.log(Level.INFO, MessageFormat.format("{0} Message listener - Stashed message from future instance {1} while Im at {2}",
                                                    config.getId(), lambda, myLambda));
                                        } 

                                        // Can drop messages from previous instances
                                        else {
                                            LOGGER.log(Level.INFO, MessageFormat.format("{0} Message listener - Dropped message from previous instance {1} while Im at {2}",
                                                    config.getId(), lambda, myLambda));
                                        }
                                        continue;
                                    }
                                }

                                handleMessage(comessage);
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
                                        MessageFormat.format("{0} Message listener - Received unknown message from {1}",
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

                    // maps values to the slot they where finalized to (after
                    // coming out from consensus)
                    Map<String, Slot> history = new HashMap<>();

                    // input values for which the observers where already notified
                    Set<String> acketToObserver = new HashSet();

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} Driver - Waiting for input",
                                config.getId()));
                    // take must be used instead of remove because it's blocking.
                    // need to do get input outside loop to bootstrap.
                    String input = this.inputs.take();

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} Driver - Got input {1}",
                                config.getId(), input));


                    while (this.running.get()) {
                        // if input was already processed after agreement, there's
                        // nothing to do besides getting a new value
                        // TODO (dsa): probably no longer need this (because beta
                        // ensures that accept value was not already decided and is valid)
                        if (history.containsKey(input)) {

                            // if not notified observers, notify
                            // (it's possible for a value to be processed, but
                            // the clients not notified if the input came late)
                            if (!acketToObserver.contains(input)) {
                                Slot slot = history.get(input);
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} Driver - Confirming {1} to client for slot in position {2}",
                                            config.getId(), input, slot.getSlotId()));
                                for (Consumer<Slot> obs: this.observers) {
                                    obs.accept(slot);
                                }
                                acketToObserver.add(input);
                            }

                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Waiting for input",
                                        config.getId()));
                            // take must be used instead of remove because it's blocking.
                            input = this.inputs.take(); // blocking
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
                        // TODO (dsa): probably no longer need this (because only
                        // one consensus is executed at a time)
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
                        if (history.containsKey(d.getValue())) {
                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - History already contained ({1}), skipping for this reason",
                                        config.getId(), d.getValue()));

                            continue;
                        }

                        // if it's not duplicate, parse
                        String value = d.getValue();
                        Optional<List<String>> parts = parseValue(value);
                        if (parts.isPresent()) {
                            String nonce = parts.get().get(0);
                            String cmd = parts.get().get(1);
                            
                            // update state
                            int slotId = updateState(cmd);
                            Slot slot = new Slot(slotId, nonce, cmd);
                            history.put(value, slot);
                        } else {
                            // TODO (dsa): raise exception, because this should not happen
                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Bad value format (should be nonce::cmd), discarding",
                                        config.getId(), d.getValue()));
                        }
                    }


                } catch (InterruptedException e) {
                    // interrup is used to stop thread
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
