package pt.ulisboa.tecnico.hdsledger.clientLibrary;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.BalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.BalanceReply;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferReply;
import pt.ulisboa.tecnico.hdsledger.communication.Message;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.logging.Level;

public class ClientStub {

    // Logger
    private static final CustomLogger LOGGER = new CustomLogger(ClientStub.class.getName());

    // Client identifier (self)
    private final ProcessConfig config;

    // Configs for everyone (replicas and clients)
    ProcessConfig[] others;

    // Link to communicate with nodes
    private final Link link;
    
    // Current request ID
    private int requestId = 0;

    private final int n;

    private ReceivedMessages receivedMessages;

    public ClientStub(int n, ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) throws HDSSException {
        this.config = clientConfig;
        this.others = nodeConfigs;
        this.n = n;
        this.link = new HMACLink(clientConfig,
						clientConfig.getPort(),
						nodeConfigs,
						LedgerMessage.class);
        this.receivedMessages = new ReceivedMessages(n);
    }

    private LedgerMessage createLedgerMessage(int id, Message.Type type, String message, int sequenceNumber) {
        LedgerMessage ledgerMessage = new LedgerMessage(id, type);
        ledgerMessage.setSequenceNumber(sequenceNumber);
        ledgerMessage.setMessage(message);
        return ledgerMessage;
    }

    public Optional<Integer> transfer(int source, int destination, int amount) {
        int currentRequestId = ++this.requestId; // nonce
        TransferRequest transferRequest = new TransferRequest(source, destination, amount);
        LedgerMessage request = createLedgerMessage(config.getId(), Message.Type.TRANSFER_REQUEST, new Gson().toJson(transferRequest), currentRequestId);
        request.signSelf(this.config.getPrivateKey());
        LOGGER.log(Level.INFO, "Sending transfer request: " + new Gson().toJson(request));

        return sendRequest(request, false);
    }

    public Optional<Integer> checkBalance(int id) {
        int currentRequestId = ++this.requestId; // nonce
        BalanceRequest balanceRequest = new BalanceRequest(id);
        LedgerMessage request = createLedgerMessage(config.getId(), Message.Type.BALANCE_REQUEST, new Gson().toJson(balanceRequest), currentRequestId);
        LOGGER.log(Level.INFO, "Sending balance request: " + new Gson().toJson(request));

        return sendRequest(request, true);
    }

    /**
     * Sends request and waits for f+1 consistent replies.
     * @param request request to send
     * @param canDiverge wheter it's possible for f+1 consistent not to appear
     */
    private Optional<Integer> sendRequest(LedgerMessage request, boolean canDiverge) {
        IntStream.range(0, n).forEach(i -> this.link.send(i, request));

        receivedMessages = new ReceivedMessages(n);

        while (!receivedMessages.hasDecided()) {
            try {
                if (receivedMessages.quorumReplied()) {
                    if (canDiverge) {
                        receivedMessages.resetRegistration();
                        IntStream.range(0, n).forEach(i -> this.link.send(i, request));
                    } else {
                        // if among 2f+1 there's not f+1 consistent, that means
                        // that correct replicas are disagreeing
                        throw new RuntimeException("divergence found among correct replicas, where no divergence was expected");
                    }
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Optional<Optional<Integer>> value = receivedMessages.getDecidedValue();

        if (value.isPresent()) {
            LOGGER.log(Level.INFO, "Value decided after f+1 confirmations");
            if (value.get().isPresent()) {
                LOGGER.log(Level.INFO, "Transaction confirmed");
            } else {
                LOGGER.log(Level.INFO, "Transaction failed");
            }
        } else {
            throw new RuntimeException("Value is not present where it should");
        }

        return value.get();
    }

    public void handleTransferReply(LedgerMessage message) {
        LOGGER.log(Level.INFO, "Received Transfer reply");
        TransferReply transferReply = message.deserializeTransferReply();

        if (transferReply.getSequenceNumber() != this.requestId) {
            LOGGER.log(Level.INFO, "Stale message received. Dropping");
            return;
        }

        if (transferReply.getSlot().isPresent()) {
            LOGGER.log(Level.INFO, "Response registered");
        } else {
            LOGGER.log(Level.WARNING, "Request was rejected");
        }

        receivedMessages.addSlot(transferReply.getSlot(), message.getSenderId());
    }

    public void handleBalanceReply(LedgerMessage message) {
        LOGGER.log(Level.INFO, "Received Balance reply");
        BalanceReply balanceReply = message.deserializeBalanceReply();

        if (balanceReply.getSequenceNumber() != this.requestId) {
            LOGGER.log(Level.INFO, "Stale message received. Dropping");
            return;
        }

        receivedMessages.addSlot(balanceReply.getValue(), message.getSenderId());
        LOGGER.log(Level.INFO, "Response registered");
    }

    public void listen() {
        try {
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        // Separate thread to handle each message
                        switch (message.getType()) {
                            case TRANSFER_REPLY -> {
                                LedgerMessage reply = (LedgerMessage) message;
                                handleTransferReply(reply);
                            }
                            case BALANCE_REPLY -> {
                                LedgerMessage reply = (LedgerMessage) message;
                                handleBalanceReply(reply);
                            }
                            case ACK, IGNORE -> LOGGER.log(Level.INFO, "Received ACK or IGNORE message. Ignoring.");
                            default -> {
                                LOGGER.log(Level.WARNING, "Received unknown message type: " + message.getType());
                                throw new HDSSException(ErrorMessage.CannotParseMessage);
                            }
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

    // class that holds the received messages for each value
    private static class ReceivedMessages {
        // replica -> confirmed value
        private final Map<Integer, Optional<Integer>> slots = new HashMap<>();

        // who replied since last reses
        private Set<Integer> replied = new HashSet<>();

        private Optional<Optional<Integer>> decision = Optional.empty();

        private final int n;

        private final int f;

        public ReceivedMessages(int n) {
            this.n = n;
            this.f = (n-1)/3;
        }

        public synchronized void addSlot(Optional<Integer> slotId, int senderId) {
            LOGGER.log(Level.INFO, "Received slot " + slotId + " from " + senderId);
            slots.putIfAbsent(senderId, slotId);
            replied.add(senderId); 

            // Histogram
            Map<Optional<Integer>, Integer> histogram = new HashMap<>();
            slots.entrySet()
                .stream()
                .map(e -> e.getValue())
                .forEach(slot -> histogram.put(slot, histogram.getOrDefault(slot, 0) + 1));

            for (Map.Entry<Integer, Optional<Integer>> e: slots.entrySet()) {
                LOGGER.log(Level.INFO, "Slot " + e.getKey() + ": " + e.getValue());
            }

            for (Map.Entry<Optional<Integer>, Integer> e: histogram.entrySet()) {
                LOGGER.log(Level.INFO, "Histogram " + e.getKey() + ": " + e.getValue());
            }

            Optional<Optional<Integer>> opt = histogram.entrySet()
                .stream()
                .filter(p -> p.getValue() > this.f)
                .map(p -> p.getKey())
                .findFirst();


            // TODO: change prints to proper logger
            if (opt.isPresent()) {
                decision = opt;
            } else {
                LOGGER.log(Level.INFO, "No decision yet");
            }
        }

        public synchronized boolean quorumReplied() {
            return replied.size() >= (2 * f + 1);
        }

        // Resets the accounting of who has replied
        public synchronized void resetRegistration() {
            this.replied = new HashSet<>();
        }

        public synchronized boolean hasDecided() {
            return decision.isPresent();
        }

        public synchronized Optional<Optional<Integer>> getDecidedValue() {
            return decision;
        }
    }
}
