package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.BalanceReply;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.BalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferReply;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.MessageCreator;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.service.StringCommand;
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

    public HDSLedgerService(ProcessConfig[] others, Link link, ProcessConfig config, NodeService nodeService) {
        this.others = others;
        this.link = link;   
        this.config = config;
        this.nodeService = nodeService;
        nodeService.registerObserver(this::decided);
    }

    private LedgerMessage createLedgerMessage(int id, Message.Type type, String serializedMessage) {
       LedgerMessage message = new LedgerMessage(id, type);

       message.setMessage(serializedMessage);

       return message;
    }

    public void transfer(LedgerMessage message) {
        TransferRequest request = message.deserializeTransferRequest();
        int clientId = message.getSenderId();
        int sequenceNumber = message.getSequenceNumber();

        // check if the source public key is valid and corresponds to the client id
        // (entity can only transfer its own funds)
        if (!request.getSourcePublicKey().equals(others[clientId].getPublicKey())) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                        "{0} - Source public key does not match client id {1}",
                        config.getId(), message.getSenderId()));
        }
        // check if the signature is consistent
        else if (!message.checkConsistentSig(others[clientId].getPublicKey())) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                        "{0} - Bad signature from client {1}",
                        config.getId(), message.getSenderId()));
        } else {
            nodeService.startConsensus(clientId, sequenceNumber, request.getSourcePublicKey(), request.getDestinationPublicKey(), request.getAmount(), message);
            return;
        }

        // If there was some failure, then reply to client saying that
        LedgerMessage reply = MessageCreator.createTransferReply(config.getId(), sequenceNumber, Optional.empty());
        link.send(clientId, reply);
    }

    public void checkBalance(LedgerMessage message) {
        BalanceRequest balanceRequest = message.deserializeBalanceRequest();
        Optional<Integer> balance;
        int clientId = message.getSenderId();

        // check if the public key is valid and corresponds to the client id
        if (!balanceRequest.getSourcePublicKey().equals(others[clientId].getPublicKey())) {
            // TODO (dgm): think about this verification
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                        "{0} - Public key does not match client id {1}",
                        config.getId(), message.getSenderId()));
            balance = Optional.empty();
        } else {
            balance = Optional.of(nodeService.getBalance(clientId));
        }

        BalanceReply balanceReply = new BalanceReply(balance, balanceRequest.getSeq());
        LedgerMessage reply = createLedgerMessage(config.getId(), Message.Type.BALANCE_REPLY, new Gson().toJson(balanceReply));
        link.send(clientId, reply);
    }

    /**
     * Receive decided value from consensus service
     * Notify the client with the decided slot
    */
    private void decided(int senderId, int seq, Optional<Integer> slotIdOpt) {
        if (slotIdOpt.isPresent()) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on slot {1} with clientId {2} and sequence number {3}",
                            config.getId(), slotIdOpt.get(), senderId, seq));
        } else {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Error found for request from {1} with sequence number {2}",
                            config.getId(), senderId, seq));
        }

        // Send the decided value to the client
        LedgerMessage reply = MessageCreator.createTransferReply(config.getId(), seq, slotIdOpt);
        link.send(senderId, reply);
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
                                case TRANSFER_REQUEST -> {
                                    LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} (HDSLedgerService) - Received transfer request from {1}",
                                            config.getId(), message.getSenderId()));
                                    transfer((LedgerMessage) message);
                                }
                                case BALANCE_REQUEST -> {
                                    LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} (HDSLedgerService) - Received balance request from {1}",
                                            config.getId(), message.getSenderId()));
                                    checkBalance((LedgerMessage) message);
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

