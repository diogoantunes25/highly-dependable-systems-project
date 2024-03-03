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
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.service.Slot;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // Nodes configurations
    private final ProcessConfig[] nodesConfig;

    // FIXME (dsa): my config ?
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
     * Returns instance with id lambda and creates one if it doesn't exist
     */
    private Instanbul getInstance(int lambda) {
        return instances.computeIfAbsent(lambda, l -> {
            Instanbul instance = new Instanbul(this.config, l);
            Consumer<String> observer = s -> {
                decided(l, s);
            };
    
            instance.registerObserver(observer);
            return instance;
        });
    }

    /*
     * Starts a new consensus instance.
     * Does not check that there's not other ongoing instances.
     *
     * @param lambda
     */
    private void actuallyInput(int lambda, String value) {
        Instanbul instance = getInstance(lambda);

        // Input into state machine
        List<ConsensusMessage> output = instance.start(value);

        // Dispatch messages
        output.forEach(m -> link.send(m.getReceiver(), m));
    }

    /*
     * Start an instance of consensus for a cmd if one is not ongoing, otherwise
     * just queues input to be evetually added to state.
     *
     * @param inputValue
     */
    public synchronized void startConsensus(String nonce, String cmd) {
        // note: add must be used instead of put as it's non-blocking
        String value = String.format("%s::%s", nonce, cmd);
        inputs.add(value); 
    }

    /**
     * Handle consensus message 
     * @param message Consensus message
     */
    private synchronized void handleMessage(ConsensusMessage message) {
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
     */
    private synchronized void decided(int lambda, String value) {

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
     */
    public int updateState(String value) {
        ledger.add(value);
        LOGGER.log(Level.INFO,
                MessageFormat.format(
                    "{0} - Current Ledger: {1}",
                    config.getId(), String.join("", ledger)));

        return ledger.size();
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        // If all upon rules are synchronized, there's no point
                        // in creating a new thread to handle each message
                        switch (message.getType()) {

                            case PRE_PREPARE, PREPARE, COMMIT ->
                                handleMessage((ConsensusMessage) message);

                            case ACK ->
                                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                        config.getId(), message.getSenderId()));

                            case IGNORE ->
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                config.getId(), message.getSenderId()));

                            default ->
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received unknown message from {1}",
                                                config.getId(), message.getSenderId()));
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();

            // Thread to take pending inputs from clients and input them into consensus
            new Thread(() -> {
                try {
                    int currentLambda = 0;
                    DecisionBucket bucket = new DecisionBucket();

                    // maps values to the slot they where finalized to (after
                    // coming out from consensus)
                    Map<String, Slot> history = new HashMap<>();

                    // input values for which the observers where already notified
                    Set<String> notifiedObservers = new HashSet();

                    // take must be used instead of remove because it's blocking.
                    // need to do get input outside loop to bootstrap.
                    String input = this.inputs.take();


                    while (true) {
                        // if input was already processed after agreement, there's
                        // nothing to do besides getting a new value
                        if (history.containsKey(input)) {

                            // if not notified observers, notify
                            // (it's possible for a value to be processed, but
                            // the clients not notified if the input came late)
                            if (!notifiedObservers.contains(input)) {
                                for (Consumer<Slot> obs: this.observers) {
                                    obs.accept(history.get(input));
                                }
                                notifiedObservers.add(input);
                            }

                            input = this.inputs.take(); // blocking
                            continue;
                        }

                        // start new instance (can't be after take, because 
                        // take's value might already be finalized as well)
                        currentLambda += 1;
                        actuallyInput(currentLambda, input);

                        // check if this instance was already decided upon. if it
                        // was, then no need to wait, otherwise wait for more
                        // decisions
                        Decision d;
                        if (bucket.contains(currentLambda)) {
                            d = bucket.get(currentLambda);
                        } else {
                            // take must be used instead of remove because it's blocking
                            d = decisions.take();
                            bucket.save(d);
                        }

                        // at this point, we have decision for instance currentLambda,
                        // we just need to process it

                        // if this consensus output is duplicate, there's nothing
                        // to be done
                        if (history.containsKey(d.getValue())) {
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

                            // if not notified observers, notify
                            if (!notifiedObservers.contains(value)) {
                                this.observers.forEach(o -> o.accept(slot));
                                notifiedObservers.add(input);
                            }

                            history.put(value, slot);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
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
}
