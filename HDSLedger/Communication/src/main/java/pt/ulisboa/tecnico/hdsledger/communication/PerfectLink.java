package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Authenticated point to point link.
 */
public class PerfectLink implements Link {

    private static final CustomLogger LOGGER = new CustomLogger(PerfectLink.class.getName());
    // Time to wait for an ACK before resending the message
    private final int BASE_SLEEP_TIME;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<Integer, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;
    // Set of received messages from specific node (prevent duplicates)
    private final Map<Integer, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private final CollapsingSet receivedAcks = new CollapsingSet();
    // Message counter
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    // Send messages to self by pushing to queue instead of through the network
    private final Queue<Message> localhostQueue = new ConcurrentLinkedQueue<>();

    public PerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this(self, port, nodes, messageClass, false, 200);
    }

    public PerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
            boolean activateLogs, int baseSleepTime) {

        this.config = self;
        this.messageClass = messageClass;
        this.BASE_SLEEP_TIME = baseSleepTime;

        Arrays.stream(nodes).forEach(node -> {
            int id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new HDSSException(ErrorMessage.CannotOpenSocket);
        }
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    public void ackSingle(Integer messageId) {
        receivedAcks.add(messageId);
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcast
     */
    public void broadcast(Message data) {
        Gson gson = new Gson();
        nodes.forEach((destId, dest) -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
    }

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    public void send(int nodeId, Message data) {
        int messageId = messageCounter.getAndIncrement();
        send(nodeId, data, messageId);
    }

    public void send(int nodeId, Message data, int messageId) {

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null) {
                    LOGGER.log(Level.WARNING,
                            MessageFormat.format("{0} - No node {1}. Failed while sending",
                                    config.getId(), nodeId));

                    throw new HDSSException(ErrorMessage.NoSuchNode);
                }

                // FIXME (dsa): fixme
                data.setMessageId(messageId);

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());
                int destPort = node.getPort();
                int count = 1;
                int sleepTime = BASE_SLEEP_TIME;

                // Send message to local queue instead of using network if destination in self
                if (nodeId == this.config.getId()) {
                    this.localhostQueue.add(data);

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} (id={4}) successfully",
                                    config.getId(), data.getType(), destAddress, destPort, nodeId));

                    return;
                }

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} (id={6}) - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++, nodeId));

                    unreliableSend(destAddress, destPort, data);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Didnt reply to message from {1} to {2}:{3} with message ID {4} (id={6}) - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++, nodeId));

                    sleepTime <<= 1;
                }

                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} (id={4}) successfully",
                        config.getId(), data.getType(), destAddress, destPort, nodeId));
            } catch (InterruptedException | UnknownHostException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /*
     * Sends a message to a specific node without guarantee of delivery
     * Mainly used to send ACKs, if they are lost, the original message will be
     * resent
     *
     * @param address The address of the destination node
     *
     * @param port The port of the destination node
     *
     * @param data The message to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, Message data) {
        new Thread(() -> {
            try {
                String serialized = new Gson().toJson(data);
                byte[] buf = serialized.getBytes();

                DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.SocketSendingError);
            }

        }).start();

    }

    public <T extends Message> Message receiveAndDeserializeWith(Class<T> targetClass, Set<Integer> disallowDuplicates) throws IOException {

        Message message;
        String serialized = "";
        boolean local = false;
        DatagramPacket response = null;

        if (!this.localhostQueue.isEmpty()) {
            message = this.localhostQueue.poll();
            local = true;
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65535];
            response = new DatagramPacket(buf, buf.length);

            socket.receive(response);

            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            serialized = new String(buffer);
            message = new Gson().fromJson(serialized, targetClass);
        }

        int senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (!nodes.containsKey(senderId))
            throw new HDSSException(ErrorMessage.NoSuchNode);

        // Handle ACKS, since it's possible to receive multiple acks from the same
        // message
        if (message.getType().equals(Message.Type.ACK)) {
            receivedAcks.add(messageId);
            return message;
        }

        // It's not an ACK -> Deserialize for the correct type
        if (!local)
            message = new Gson().fromJson(serialized, targetClass);

        Type originalType = message.getType();
        if (disallowDuplicates.contains(senderId)) {
            boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
            // Message already received (add returns false if already exists) => Discard
            if (isRepeated && !targetClass.equals(HMACMessage.class)){
                message.setType(Message.Type.IGNORE);
            }
        }

        switch (message.getType()) {
            case APPEND_REQUEST, APPEND_REPLY -> {
                AppendMessage request = (AppendMessage) message;
                //TODO (cfc)
                if (request.getReplyTo() == config.getId())
                    receivedAcks.add(request.getReplyToMessageId());
                return message;
            }
            case PRE_PREPARE -> {
                return message;
            }
            case IGNORE -> {
                if (!originalType.equals(Type.COMMIT))
                    return message;
            }
            case PREPARE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() == config.getId())
                    receivedAcks.add(consensusMessage.getReplyToMessageId());

                return message;
            }
            case COMMIT -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() == config.getId())
                    receivedAcks.add(consensusMessage.getReplyToMessageId());
                return consensusMessage;
            }
            case KEY_PROPOSAL -> {
                System.out.println("Received KEY_PROPOSAL");
                KeyProposal keyProposalMessage = new Gson().fromJson(serialized, KeyProposal.class);
                if (keyProposalMessage.getReplyTo() == config.getId())
                    receivedAcks.add(keyProposalMessage.getReplyToMessageId());
                return keyProposalMessage;
            }

            case HMAC -> {
                System.out.println("Received HMAC");
                HMACMessage hmacMessage = new Gson().fromJson(serialized, HMACMessage.class);
                if (hmacMessage.getReplyTo() == config.getId())
                    receivedAcks.add(hmacMessage.getReplyToMessageId());
                return hmacMessage;
            }
            default -> {}
        }

        if (!local && !targetClass.equals(HMACMessage.class)) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(messageId);
            responseMessage.setReceiver(senderId);

            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            unreliableSend(address, port, responseMessage);
        }
        return message;
    }

    /*
     * Receives a message from any node in the network (blocking)
     */
    public Message receive() throws IOException {
        return receiveAndDeserializeWith(messageClass, nodes.keySet());
    }
}
