package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendReply;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class HDSLedgerService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    
    // Other participants configurations configurations
    private final ProcessConfig[] others;

    // Current node config
    private final ProcessConfig config;

    // Link to communicate with nodes
    private final Link link;

    // Node service that allows start consensus instances
    private final NodeService nodeService;

    // Confirmed queue
    private Queue<String> confirmed = new LinkedList<>();

    public HDSLedgerService(ProcessConfig[] others, Link link, ProcessConfig config, NodeService nodeService) {
        this.others = others;
        this.link = link;   
        this.config = config;
        this.nodeService = nodeService;
        nodeService.registerObserver(s -> decided(s));
    }

    private AppendMessage createAppendReplyMessage(int id, int receiver, String value, int sequenceNumber, int slot) {
        AppendReply appendReply = new AppendReply(value, sequenceNumber, slot);

        AppendMessage message = new AppendMessage(id, Message.Type.APPEND_REPLY, receiver);

        message.setMessage(new Gson().toJson(appendReply));

        return message;
    }

    public void append(AppendMessage message) {
        AppendRequest request = message.deserializeAppendRequest();


        // Send the value to the consensus service
        int sequenceNumber = request.getSequenceNumber();
        int clientId = message.getSenderId();

        if (!message.checkConsistentSig(others[clientId].getPublicKey())) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                        "{0} - Bad signature from client {1}",
                        config.getId(), clientId));
            return;
        }
        LOGGER.log(Level.INFO,
                MessageFormat.format(
                    "{0} - Append request from client {1} (signature check passed)",
                    config.getId(), clientId));

        String value = request.getValue();
        nodeService.startConsensus(clientId, sequenceNumber, value, message);
    }

    /**
     * Receive decided value from consensus service
     * Notify the client with the decided slot
    */
    private void decided(Slot slot) {

        int slotId = slot.getSlotId();
        int clientId = slot.getClientId();
        int sequenceNumber = slot.getSeq();
        String value = slot.getMessage();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Decided on slot {1} value {2}, clientId {3} and sequence number {4}",
                        config.getId(), slotId, value, clientId, sequenceNumber));

        // Send the decided value to the client
        AppendMessage reply = createAppendReplyMessage(config.getId(), clientId, value, sequenceNumber, slotId);
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

                                case APPEND_REQUEST -> {
                                    System.out.println("Received request: "+ message.getClass().getName());
                                    append((AppendMessage) message);
                                }
                                default ->
                                    LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} (HDSLedgerService) - Received unknown message (of type {2}) from {1}",
                                            config.getId(), message.getSenderId(), message.getType()));  
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

