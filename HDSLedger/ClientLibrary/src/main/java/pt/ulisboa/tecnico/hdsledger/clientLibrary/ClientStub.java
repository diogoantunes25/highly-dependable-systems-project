package pt.ulisboa.tecnico.hdsledger.clientLibrary;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.*;

import pt.ulisboa.tecnico.hdsledger.consensus.message.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrepareMessage;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientStub {

    // Client identifier (self)
    private final ProcessConfig config;

    // Link to communicate with nodes
    private final Link link;

    // Map of responses from nodes
    private final Map<Integer, AppendRequest> responses = new HashMap<>(); // TODO - Change AppendRequest to appropriate type of Response
    
    // Current request ID
    private AtomicInteger requestId = new AtomicInteger(0);

    private final Integer nodesNumber;

    private final ReceivedSlots receivedSlots;

    public ClientStub(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs,
                   boolean activateLogs) throws HDSSException {
        this.config = clientConfig;

        // Create link to communicate with nodes
        this.link = new APLink(clientConfig, clientConfig.getPort(), nodeConfigs, ConsensusMessage.class, activateLogs, 5000);
        // TODO - ConsensusMessage is not correct i think

        this.nodesNumber = nodeConfigs.length;

        this.receivedSlots = new ReceivedSlots(this.nodesNumber);
    }

    public int append(String value)  throws InterruptedException{
        int currentRequestId = this.requestId.getAndIncrement(); // nonce
        AppendRequest request = new AppendRequest(config.getId(), Message.Type.APPEND_REQUEST, value, currentRequestId);

        String key = String.format("%s_%s", value, currentRequestId);
        this.link.broadcast(request);

        while (!receivedSlots.hasDecided(key)) {
            wait();
        }

        int slotId = receivedSlots.getDecidedSlot(key);
        
        return slotId;
    }

    public void handleAppendReply(AppendReply appendReply) {
        String key = String.format("%s_%s", appendReply.getValue(), appendReply.getSequenceNumber());
        receivedSlots.addSlot(key, appendReply.getSlot());
    }

    public void listen() {
        try {
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        // Separate thread to handle each message
                        switch (message.getType()) {
                            case APPEND_REPLY -> {
                                AppendReply reply = (AppendReply) message;
                                handleAppendReply(reply);
                            }
                            case ACK -> {
                                continue; // maybe add to logger?
                            }
                            default -> {
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

    // class that holds the received slots for each value   
    private static class ReceivedSlots {
        private final Map<String, Map<Integer,AtomicInteger>> slots = new ConcurrentHashMap<>();

        private final Map<String, Integer> decidedSlots = new ConcurrentHashMap<>();

        private final int nodesNumber;

        public ReceivedSlots(int nodesNumber) {
            this.nodesNumber = nodesNumber;
        }

        public void addSlot(String key, int slotId) {
            int decidesNeeded = (this.nodesNumber - 1) / 3;
            if (decidedSlots.containsKey(key)) {
                return;
            }
            slots.putIfAbsent(key, new ConcurrentHashMap<>());
            slots.get(key).putIfAbsent(slotId, new AtomicInteger(0));
            int confirmedSlot = slots.get(key).get(slotId).incrementAndGet();
            if (confirmedSlot >= decidesNeeded) {
                decidedSlots.put(key, slotId);
                notify();
            }
        }

        public void addDecidedSlot(String value, int slotId) {
            decidedSlots.put(value, slotId);
        }

        public boolean hasDecided(String key) {
            return decidedSlots.containsKey(key);
        }

        public Integer getDecidedSlot(String key) {
            return decidedSlots.get(key);
        }

    }
}
