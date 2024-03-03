package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.function.Consumer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    // Current consensus instance (i.e. latest consensus instance I've inputed to)
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);

    // Ledger (for now, just a list of strings)
    // TODO (dsa): factor out to a state class
    private ArrayList<String> ledger = new ArrayList<String>();

    // We'll allow multiple instances to run in parallel if needed, so this
    // map needs to be thread-safe
    // Maps: lambda -> instance
    private Map<Integer, Instanbul> instances = new ConcurrentHashMap<>();

    // Stores input values for each instance that start/is planned to start
    private Map<Integer, String> inputs = new ConcurrentHashMap<>();

    // Callback to call when a new input is finalized by consensus
    private Queue<Consumer<Slot>> observers = new ConcurrentLinkedQueue<>();

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig[] nodesConfig) {
        this.link = link;
        this.config = config;
        this.nodesConfig = nodesConfig;
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
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
     * Tries to start instance with id lambda.
     *
     * @param lambda
     */
    private synchronized void tryStartConsensus(int lambda) {
        // TODO (dsa): lock on a per instance basis
        Instanbul instance = getInstance(lambda);
        String value = inputs.get(lambda);

        // No input has been provided for this instance yet
        if (value == null) {
            return;
        }

        // If previous ended or it's the first, start the instance
        // We need to be sure that the previous value has been decided
        if (lastDecidedConsensusInstance.get() < lambda - 1) {
            return;
        }

        // Input into state machine
        List<ConsensusMessage> output = instance.start(value);

        // Dispatch messages
        output.forEach(m -> link.send(m.getReceiver(), m));
    }

    /*
     * Start an instance of consensus for a value
     * @param inputValue
     */
    public synchronized void startConsensus(String nonce, String value) {
        // TODO: synchronize on a per instance basis

        // Set initial consensus values
        int smallestNotInputted = this.consensusInstance.incrementAndGet();
        inputs.put(smallestNotInputted, value);
        tryStartConsensus(smallestNotInputted);
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
     * Handle decidion for instance lambda
     */
    private void decided(int lambda, String value) {
        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Decided on Consensus Instance {1} with value {2}",
                        config.getId(), lambda, value));

        // Append value to the ledger (must be synchronized to be thread-safe)
        synchronized(ledger) {

            // Increment size of ledger to accommodate current instance
            ledger.ensureCapacity(lambda);
            while (ledger.size() < lambda - 1) {
                ledger.add("");
            }

            ledger.add(lambda - 1, value);

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                        "{0} - Current Ledger: {1}",
                        config.getId(), String.join("", ledger)));
        }

        lastDecidedConsensusInstance.incrementAndGet();
        
        // Try to start next round
        tryStartConsensus(lambda+1);
    }

    /**
     * Registers observer for confirmation of finalization of inputted values
     * @param observer Callback function
     */
    public void registerObserver(Consumer<Slot> observer) {
        this.observers.add(observer);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
