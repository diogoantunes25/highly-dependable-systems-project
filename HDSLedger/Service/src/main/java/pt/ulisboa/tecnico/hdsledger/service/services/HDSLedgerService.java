package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendReply;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.consensus.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class HDSLedgerService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    
    // Clients configurations
    private final ProcessConfig[] clientsConfig;

    // Current node config
    private final ProcessConfig config;

    // Link to communicate with nodes
    private final Link link;

    // Node service that allows start consensus instances
    private final NodeService nodeService;

    // Confirmed queue
    private Queue<String> confirmed = new LinkedList<>();

    public HDSLedgerService(ProcessConfig[] clientConfigs, Link link, ProcessConfig config, NodeService nodeService) {
        this.clientsConfig = clientConfigs;
        this.link = link;   
        this.config = config;
        this.nodeService = nodeService;
        nodeService.registerObserver(s -> decided(s));
    }

    public void append(AppendRequest request) {
        // Send the value to the consensus service
        int sequenceNumber = request.getSequenceNumber();
        int clientId = request.getSenderId();
        String value = request.getValue();
        String nonce = Integer.toString(clientId) + ":" + Integer.toString(sequenceNumber);
        nodeService.startConsensus(nonce, value);
    }

    /**
     * Receive decided value from consensus service
     * Notify the client with the decided slot
    */
    private void decided(Slot slot) {

        int slotId = slot.getSlotId();
        String nonce = slot.getNonce();
        String value = slot.getMessage();
        String[] parts = value.split("_");
        int clientId = Integer.parseInt(parts[0]);
        int sequenceNumber = Integer.parseInt(parts[1]);

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Decided on slot {1} value {2} and nonce {3}",
                        config.getId(), slotId, value, nonce));

        // Send the decided value to the client
        Message reply = new AppendReply(config.getId(), Message.Type.APPEND_REPLY, value, sequenceNumber, slotId);
        link.send(clientId, reply);
        
    }



    @Override
    public void listen() {
        List<Thread> threads = new ArrayList<>();
        try {
            // Thread to listen on every request
            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {

                                case APPEND_REQUEST ->
                                    append((AppendRequest) message);

                                default ->
                                    LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received unknown message from {1}",
                                            config.getId(), message.getSenderId()));  

                            }

                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            });
            t.start();
            threads.add(t);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopAndWait() {
        // TODO (dsa)
    }
}

