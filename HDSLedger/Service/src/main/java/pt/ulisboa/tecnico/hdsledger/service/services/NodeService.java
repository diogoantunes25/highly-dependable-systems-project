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
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import javafx.util.Pair;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.consensus.Istanbul;
import pt.ulisboa.tecnico.hdsledger.consensus.Timer;
import pt.ulisboa.tecnico.hdsledger.consensus.SimpleTimer;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.service.State;
import pt.ulisboa.tecnico.hdsledger.service.StringState;
import pt.ulisboa.tecnico.hdsledger.service.BankState;
import pt.ulisboa.tecnico.hdsledger.service.StringCommand;
import pt.ulisboa.tecnico.hdsledger.service.BankCommand;
import pt.ulisboa.tecnico.hdsledger.service.CommandBatch;
import pt.ulisboa.tecnico.hdsledger.service.Command;
import pt.ulisboa.tecnico.hdsledger.service.ObserverAck;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // TODO (dsa): resource file or argument or env var
    private static final String DEFAULT_GENESIS_FILE = "/tmp/genesis.json";

    // TODO (dsa): resource file or argument or env var
    private static final int BATCH_SIZE = 5;

    // Time waited to form new batch (adjusted according to the cost of forming
    // batches)
    private static final int BATCH_TIMEOUT = 50; // milliseconds

    // TODO (dsa): find better fee policy
    public static final int DEFAULT_FEE = 1;

    // Nodes configurations
    private final List<ProcessConfig> others;

    // My configuration
    private final ProcessConfig config;

    // Link to communicate with nodes
    private final Link link;

    // Ledger (for now, just a list of strings)
    // TODO (dsa): factor out to a state class
    private BankState ledger = new BankState();

    // We'll allow multiple instances to run in parallel if needed, so this
    // map needs to be thread-safe
    // Maps: lambda -> instance
    private Map<Integer, Istanbul> instances = new ConcurrentHashMap<>();

    // Callback to call when a new input is finalized by consensus (only if
    // the input was provided by this replica)
    private Queue<ObserverAck> observers = new ConcurrentLinkedQueue<>();

    // Blocking queue of pending inputs (thread-safe)
    private BlockingQueue<BankCommand> inputs = new LinkedBlockingQueue<>();

    // Blocking queue of decisions (thread-safe)
    private BlockingQueue<Decision<CommandBatch>> decisions = new LinkedBlockingQueue<>(); 

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

    // List of paths to all public keys
    private List<String> allKeys;

    // List of paths to client public keys
    private List<String> clientPks;

    // maps values to the slot they were finalized to (after
    // coming out from consensus). slot is empty is the command failed
    Map<BankCommand, Slot<BankCommand>> history = new ConcurrentHashMap<>();

    // Reverse lookup of ids given hash of public key
    Map<String, Integer> idLookUp;

    public NodeService(Link link, ProcessConfig config,
                       ProcessConfig[] nodesConfig, List<String> clientPks, String genesisFilePath) {
        this.link = link;
        this.config = config;
        this.others = Arrays.asList(nodesConfig);
        this.clientPks = clientPks;
        this.allKeys = getAllKeys(nodesConfig, clientPks);

        Map<Integer, Integer> initalBalances = loadGenesisFromFile(genesisFilePath);
        genesis(initalBalances);
    }

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig[] nodesConfig, List<String> clientPks) {
        this(link, config, nodesConfig, clientPks, DEFAULT_GENESIS_FILE);
    }

    public List<String> getAllKeys(ProcessConfig[] nodesConfig, List<String> clientPks) {
        return Stream.concat(
            Arrays.stream(nodesConfig).map(config -> config.getPublicKey()),
            clientPks.stream()
        ).collect(Collectors.toList());
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    private Map<Integer, Integer> loadGenesisFromFile(String filename) {
        try (InputStreamReader reader = new FileReader(filename)) {
            Gson gson = new Gson();
            JsonArray array = gson.fromJson(reader, JsonArray.class);

            Map<Integer, Integer> initialBalances = new HashMap<>();
            for (JsonElement element: array) {
                JsonObject obj = element.getAsJsonObject();
                int clientId = obj.get("id").getAsInt();
                int balance = obj.get("balance").getAsInt();
                initialBalances.put(clientId, balance);
            }

            return initialBalances;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initializes state.
     */
    void genesis(Map<Integer, Integer> initialBalances) {
        for (Map.Entry<Integer,Integer> entry: initialBalances.entrySet()) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Spawning money: {1} added to {2}",
                config.getId(), entry.getValue(), entry.getKey()));
            this.ledger.spawnMoney(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Check that value is valid
     */
    private boolean checkIsValidValue(int lambda, String value) {
        Optional<CommandBatch> cmdOpt = CommandBatch.deserialize(value);
        if (!cmdOpt.isPresent()) {
            return false;
        }

        CommandBatch cmd = cmdOpt.get();

        if (lambda != this.currentLambda.get()) {
            LOGGER.log(Level.WARNING, MessageFormat.format("{0} - asking to determine validity of transaction in the past/future. can't to that, so saying it's invalid",
                    config.getId()));

            return false;
        }

        // Check that it can be applied on the current state
        if (!this.ledger.allTransactionsValid(cmd)) {
            LOGGER.log(Level.WARNING, MessageFormat.format("{0} - some transaction in the batch is invalid, rejecting",
                    config.getId()));

            return false;
        }

        // Check that everything in command is properly signed
        return cmd.check(this.allKeys, DEFAULT_FEE);
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

            if (lambda > this.currentLambda.get()) {
                throw new RuntimeException("Trying to get instance for future instance. Can't determine the beta predicate");
            }

            if (lambda < this.currentLambda.get()) {
                throw new RuntimeException("Trying to create new instance for past instance. This should not happen. Probably shouldn't have moved to current instance");
            }

            Istanbul instance = new Istanbul(this.others, this.config, l, value -> this.checkIsValidValue(l, value));
            Timer timer = new SimpleTimer();
            Consumer<String> observer = s -> {
                decided(l, s);
            };
    
		    timer.registeTimeoutCallback(round -> {
                List<ConsensusMessage> output = instance.handleTimeout(round);
                output.stream().forEach(m -> link.send(m.getReceiver(), m));
            });
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
     * @param clientId client that requested the command
     * @param seq Sequence number sent by client that uniquely identifies transaction
     * (for that client)
     * @param sourcePublicKey path to source public key
     * @param destinationPublicKey path to destination public key
     * @param amount amount of funds to transfer
     * @param proof message proving that transfer was requested by the source
     */
    public void startConsensus(int clientId, int seq, int source, int destination, int amount, LedgerMessage proof) {
        // note: add must be used instead of put as it's non-blocking
        inputs.add(new BankCommand(clientId, seq, source, destination, amount, this.config.getId(), DEFAULT_FEE, proof));
    }

    /**
     * Gets balance for client with provided id
     * Assumes clearance was already verified
     */
    public synchronized int getBalance(int clientId) {
        return this.ledger.getBalance(clientId);
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

        Optional<CommandBatch> cmdOpt = CommandBatch.deserialize(value);
        if (!cmdOpt.isPresent()) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Invalid value came out of consensus instance {1} {2} - discarding",
                            config.getId(), lambda, value));
            return;
        }

        Decision<CommandBatch> d = new Decision<>(lambda, cmdOpt.get());
        decisions.add(d);
    }

    /**
     * Registers observer for confirmation of finalization of inputted values
     * @param observer Callback function
     * Thread-safe.
     */
    public void registerObserver(ObserverAck observer) {
        this.observers.add(observer);
    }

    public Map<String, Integer> getLedger() {
        Map<String, Integer> map = new HashMap<>();
        this.ledger.getState().forEach((id, bal) -> map.put(SigningUtils.publicKeyHash(this.allKeys.get(id)), bal));
        return map;
    }

    /**
     * Updates state and returns slot position for value
     * Non thread-safe.
     */
    public synchronized Optional<Integer> updateState(CommandBatch cmds) {
        Optional<Integer> slotIdOpt = ledger.update(cmds);
        
        if (slotIdOpt.isPresent()) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Map.Entry<Integer, Integer> entry : ledger.getState().entrySet()) {
                stringBuilder.append("\t").append(entry.getKey()).append(": ").append(entry.getValue()).append(", ");
            }

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                        "{0} - Current Ledger (after slot with id {2}): {1}",
                        config.getId(), stringBuilder.toString(), slotIdOpt.get()));
        } else {
            LOGGER.log(Level.SEVERE,
                    MessageFormat.format(
                        "{0} - Tried to update state with invalid command - {1}",
                        config.getId(), cmds));
        }

        return slotIdOpt;
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
                                LOGGER.log(Level.FINE, MessageFormat.format("{0} Message listener - Received ACK message from {1}",
                                        config.getId(), message.getSenderId()));

                            case IGNORE ->
                                LOGGER.log(Level.FINE,
                                        MessageFormat.format("{0} Message listener - Received IGNORE message from {1}",
                                                config.getId(), message.getSenderId()));

                            default ->
                                LOGGER.log(Level.FINE,
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
                    DecisionBucket<CommandBatch> bucket = new DecisionBucket<>();

                    // input values for which the observers where already notified
                    Set<BankCommand> acketToObserver = new HashSet<>();

                    List<BankCommand> pendingInputs = new ArrayList<>();

                    List<BankCommand> notConfirmed;

                    Pair<List<BankCommand>, List<BankCommand>> parsedInputs;

                    List<BankCommand> valid, invalid, proposed;

                    valid = new ArrayList<>();
                    invalid = new ArrayList<>();

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} Driver - Waiting for input",
                                config.getId()));

                    // take must be used instead of remove because it's blocking.
                    // need to do get input outside loop to bootstrap.
                    pendingInputs.add(this.inputs.take());
                    this.inputs.drainTo(pendingInputs);

                    // Invalid requests are ignored (replying that is wrong would
                    // might raise some problems - if another replica sees it as
                    // valid, maybe because it has another transaction that makes
                    // it valid, the consensus layer might output the transaction
                    // and I already said it was invalid

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} Driver - Got {1} inputs ",
                                config.getId(), pendingInputs.size()));

                    while (this.running.get()) {
                        // if input was already processed after agreement, there's
                        // nothing to do besides getting a new value
                        // TODO (dsa): probably no longer need this (because beta
                        // ensures that accept value was not already decided and is valid)
                        
                        // List of commands that are not in the history
                        notConfirmed = new ArrayList<>();

                        for (BankCommand input: pendingInputs) {
                            // Check if input already in history (not considering proof)
                            if (history.containsKey(input)) {

                                // if not notified observers, notify
                                // (it's possible for a value to be processed, but
                                // the clients not notified if the input came late)
                                if (!acketToObserver.contains(input)) {
                                    Slot<BankCommand> slot = history.get(input);
                                    BankCommand cmd = slot.getCmd();

                                    if (slot.getSlotId().isPresent()) {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} Driver - Confirming {1} to client for slot in position {2}",
                                                    config.getId(), input, slot.getSlotId()));
                                    } else {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} Driver - Telling the client that {1} was not good to be executed",
                                                    config.getId(), input));
                                    }

                                    for (ObserverAck obs: this.observers) {
                                        obs.ack(cmd.getClientId(), cmd.getSeq(), slot.getSlotId());
                                    }
                                    
                                    acketToObserver.add(input);
                                }
                            } else {
                                notConfirmed.add(input);
                            }
                        }

                        parsedInputs = this.ledger.getValidFromBatch(notConfirmed);
                        valid = parsedInputs.getKey();

                        pendingInputs = valid;

                        // If there are not enough valid request, then get more inputs
                        if (valid.size() < BATCH_SIZE) {
                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} Driver - Waiting for input",
                                        config.getId()));


                            // If really there's nothing valid to propose, we
                            // need to wait until a new input comes in. If there's
                            // something, we'll wait for a timeout only to ensure
                            // liveness
                            if (valid.size() == 0) {
                                pendingInputs.add(this.inputs.take());
                                this.inputs.drainTo(pendingInputs);
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} Driver - Got {1} inputs after blocking",
                                            config.getId(), inputs.size(), valid.size()));
                                continue;
                            } else {
                                BankCommand newCmd = this.inputs.poll(BATCH_TIMEOUT, TimeUnit.MILLISECONDS);
                                if (newCmd != null) {
                                    pendingInputs.add(newCmd);
                                    this.inputs.drainTo(pendingInputs);
                                    continue;
                                }
                                else {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} Driver - Timed out waiting for inputs",
                                                config.getId(), inputs.size(), valid.size()));
                                    // doesn't continue (i.e. start loop from the top), because
                                    // it's as if the batch was full
                                }
                            }
                        }

                        // start new instance (can't be after take, because 
                        // take's value might already be finalized as well)
                        currentLambda.incrementAndGet();
                        LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} Driver - Inputting {2} values into consensus {1}",
                                    config.getId(), currentLambda.get(), valid.size()));

                        // Remove first BATCH_SIZE from valid list
                        proposed = valid.subList(0, Math.min(valid.size(), BATCH_SIZE));

                        CommandBatch input = new CommandBatch(proposed);
                        actuallyInput(currentLambda.get(), input.serialize());

                        LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} Driver - Inputted, now I wait for decision",
                                    config.getId()));

                        // check if this instance was already decided upon. if it
                        // was, then no need to wait, otherwise wait for more
                        // decisions
                        Decision<CommandBatch> d;
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

                        CommandBatch cmds = d.getValue();

                        LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} Driver - Updating state",
                                    config.getId(), currentLambda.get()));

                        Optional<Integer> slotIdOpt = updateState(cmds);

                        for (BankCommand cmd: cmds.getCommands()) {
                            history.put(cmd, new Slot<>(slotIdOpt, cmd));
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
    private static class Decision<C extends Command> {
        private int lambda;
        private C value;

        Decision(int lambda, C value) {
            this.lambda = lambda;
            this.value = value;
        }

        int getLambda() {
            return this.lambda;
        }

        C getValue() {
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
    private static class DecisionBucket<C extends Command> {
        private Map<Integer, Decision<C>> bucket = new HashMap<>();

        DecisionBucket() { }

        /**
         * Stores decision d in bucket
         */
        void save(Decision<C> d) {
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
        Decision<C> get(int lambda) {
            return bucket.get(lambda);
        }
    }

    // Mostly for testing purposes
    public int getId() {
        return this.config.getId();
    }
}
