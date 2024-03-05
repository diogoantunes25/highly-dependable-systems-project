package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;
import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.*;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
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
public class HMACLink implements Link {

    private static final CustomLogger LOGGER = new CustomLogger(APLink.class.getName());
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
    // Set of shared keys with other nodes
    private final Map<Integer, Key> sharedKeys = new ConcurrentHashMap<>();

    public HMACLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this(self, port, nodes, messageClass, false, 200);
    }

    public HMACLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
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
        setup(self, nodes);
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcasted
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

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null)
                    throw new HDSSException(ErrorMessage.NoSuchNode);

                data.setMessageId(messageCounter.getAndIncrement());

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());
                int destPort = node.getPort();
                int count = 1;
                int messageId = data.getMessageId();
                int sleepTime = BASE_SLEEP_TIME;

                // Send message to local queue instead of using network if destination in self
                if (nodeId == this.config.getId()) {
                    this.localhostQueue.add(data);

                    // LOGGER.log(Level.INFO,
                    //         MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} successfully",
                    //                 config.getId(), data.getType(), destAddress, destPort));

                    return;
                }

                for (;;) {
                    // LOGGER.log(Level.INFO, MessageFormat.format(
                    //         "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}", config.getId(),
                    //         data.getType(), destAddress, destPort, messageId, count++));

                    unreliableSend(destAddress, destPort, data);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                // LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} successfully",
                //         config.getId(), data.getType(), destAddress, destPort));
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
                String jsonString = new Gson().toJson(data);

                // try to get appropriate key from sharedKeys
                if (sharedKeys.containsKey(data.getReceiver())) {
                    Key sharedKey = sharedKeys.get(data.getReceiver());
                    String hmac = SigningUtils.generateHMAC(jsonString, sharedKey);
                    HMACMessage hmacMessage = new HMACMessage(jsonString, hmac);
                    byte[] buf = new Gson().toJson(hmacMessage).getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
                    socket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }

    /*
     * Receives a message from any node in the network (blocking)
     */
    public Message receive() throws IOException, ClassNotFoundException {

        Message message = null;
        String serialized = "";
        Boolean local = false;
        DatagramPacket response = null;

        if (this.localhostQueue.size() > 0) {
            message = this.localhostQueue.poll();
            local = true;
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65535];
            response = new DatagramPacket(buf, buf.length);

            socket.receive(response);

            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());

            // buffer can be either a HMACMessage or a KeyProposal message

            // assume that it is a KeyProposal message
            try {
                // first we decrypt the message
                String decryptedData = new String(SigningUtils.decrypt(buffer, config.getPrivateKey()));
                // decrypted data is a KeyProposal message from the sender, so we need to verify the signature
                message = new Gson().fromJson(decryptedData, KeyProposal.class);
                // verify signature
                if (!SigningUtils.verifySignature(((KeyProposal) message).getKey(),
                        ((KeyProposal) message).getSignature(),
                        this.nodes.get(message.getSenderId()).getPublicKey())) {
                    message.setType(Message.Type.IGNORE);
                    LOGGER.log(Level.WARNING,  MessageFormat.format(
                            "WARNING: Invalid message signature received from {0}:{1}",
                            InetAddress.getByName(response.getAddress().getHostName()), response.getPort()));
                    return message;
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException |
                     NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                     BadPaddingException e) {
                // if we catch an exception, we assume that it is a HMACMessage
                HMACMessage hmacMessage = new Gson().fromJson(new String(buffer), HMACMessage.class);
                String hmac = hmacMessage.getHmac();
                String messageString = hmacMessage.getMessage();
                // try to get appropriate key from sharedKeys
                if (sharedKeys.containsKey(message.getSenderId())) {
                    Key sharedKey = sharedKeys.get(message.getSenderId());
                    if (!hmac.equals(SigningUtils.generateHMAC(messageString, sharedKey))) {
                        message.setType(Message.Type.IGNORE);
                        LOGGER.log(Level.WARNING,  MessageFormat.format(
                                "WARNING: Invalid message signature received from {0}:{1}",
                                InetAddress.getByName(response.getAddress().getHostName()), response.getPort()));
                        return message;
                    }
                }
            }
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
            message = new Gson().fromJson(serialized, this.messageClass);

        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        Type originalType = message.getType();
        // Message already received (add returns false if already exists) => Discard
        if (isRepeated) {
            message.setType(Message.Type.IGNORE);
        }

        switch (message.getType()) {
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
            }
            case KEY_PROPOSAL -> {
                KeyProposal keyProposal = (KeyProposal) message;
                String key = keyProposal.getKey();
                byte[] keyBytes = key.getBytes();
                Key aesKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");

                sharedKeys.put(message.getSenderId(), aesKey);
                return message;
            }
            default -> {}
        }

        if (!local) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(messageId);

            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            unreliableSend(address, port, responseMessage);
        }

        return message;
    }

    /**
     * Tries to receive a message from any node in the network (non-blocking)
     */
    public Optional<Message> tryReceive() throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException("TODO");
    }

    public void setup(ProcessConfig self, ProcessConfig[] nodes) {
        new Thread(() -> {
            // setup shared keys between channels
            for (ProcessConfig dest : nodes) {
                if (dest.getId() < self.getId()) {  // only send to nodes with higher id
                    continue;
                }

                try {
                    Key aesKey = SigningUtils.generateSimKey();
                    // store key in sharedKeys
                    sharedKeys.put(dest.getId(), aesKey);

                    String aesKeyString = Base64.getEncoder().encodeToString(aesKey.getEncoded());
                    Optional<String> signature;
                    // Sign message
                    try {
                        // serialize keyProposal
                        String serizalize = new Gson().toJson(aesKeyString);
                        signature = Optional.of(SigningUtils.sign(serizalize, this.config.getPrivateKey()));
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new HDSSException(ErrorMessage.SigningError);
                    }

                    KeyProposal keyProposal = new KeyProposal(this.config.getId(), aesKeyString, signature.get());
                    keyProposal.setReceiver(dest.getId());
                    keyProposal.setMessageId(messageCounter.getAndIncrement());
                    // encrypt message with receiver's public key
                    byte[] encryptedData;
                    try {
                        String serializedKeyProposal = new Gson().toJson(keyProposal);
                        encryptedData = SigningUtils.encrypt(serializedKeyProposal.getBytes(), dest.getPublicKey());
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException |
                             NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                             BadPaddingException e) {
                        e.printStackTrace();
                        throw new HDSSException(ErrorMessage.EncryptionError);
                    }

                    DatagramPacket packet = new DatagramPacket(encryptedData, encryptedData.length, InetAddress.getByName(dest.getHostname()), dest.getPort());
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new HDSSException(ErrorMessage.SocketSendingError);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw new HDSSException(ErrorMessage.GeneratingKeyError);
                }
            }
        } ).start();
    }
}
