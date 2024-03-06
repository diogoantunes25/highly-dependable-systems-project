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
import java.lang.management.MemoryUsage;
import java.net.*;
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

    private final APLink apLink;

    public HMACLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this(self, port, nodes, messageClass, false, 200);
    }

    public HMACLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
                    boolean activateLogs, int baseSleepTime) {

        this.apLink = new APLink(self, port, nodes, messageClass, activateLogs, baseSleepTime);

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
        new Thread(() -> {
            int backoff = 10;
            while (sharedKeys.get(nodeId) == null) {
                try {
                    // exponential backoff
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            String dataString = new Gson().toJson(data);
            Key sharedKey = sharedKeys.get(data.getReceiver());
            String hmac = SigningUtils.generateHMAC(dataString, sharedKey);
            HMACMessage hmacMessage = new HMACMessage(data.getSenderId(), data.getType(), dataString, hmac);
            apLink.send(nodeId, hmacMessage);
        }).start();
    }

    public void unreliableSend(InetAddress address, int port, Message data) {
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
            // assume that it is an HMAC message as it is cheaper to check

            // try to get appropriate key from sharedKeys
            if (sharedKeys.containsKey(message.getSenderId())) {
                // If we already have the key than it is probably a HMACMessage, it is unlikely that we receive a KeyProposal
                // if it is an HMAC Message, but we do not have the key, we ignore the message as there's nothing we can do ?
                message = new Gson().fromJson(new String(buffer), HMACMessage.class);
                // verify hmac
                String hmac = ((HMACMessage) message).getHmac();
                String messageString = ((HMACMessage) message).getMessage();
                Key sharedKey = sharedKeys.get(message.getSenderId());
                if (!hmac.equals(SigningUtils.generateHMAC(messageString, sharedKey))) {
                    // if the hmac is invalid, we ignore the message as it is not valid
                    message.setType(Message.Type.IGNORE);
                    LOGGER.log(Level.WARNING, MessageFormat.format(
                            "WARNING: Invalid message HMAC received from {0}:{1}",
                            InetAddress.getByName(response.getAddress().getHostName()), response.getPort()));
                    return message;
                }
            } else {  // if we do not have a sharedKey than the message probably is a Key Proposal
                try {
                    // the received data is a Key Proposal object with the fields encrypted
                    message = new Gson().fromJson(new String(buffer), KeyProposal.class);
                    // decrypt keyProposal fields
                    ((KeyProposal) message).setKey(SigningUtils.decrypt(((KeyProposal) message).getKey().getBytes(), this.config.getPrivateKey()));
                    ((KeyProposal) message).setSignature(SigningUtils.decrypt(((KeyProposal) message).getSignature().getBytes(), this.config.getPrivateKey()));
                    // verify signature
                    if (!SigningUtils.verifySignature(((KeyProposal) message).getKey(),
                            ((KeyProposal) message).getSignature(),
                            this.nodes.get(message.getSenderId()).getPublicKey())) {
                        // if the signature is invalid, we ignore the message as it is not valid
                        message.setType(Message.Type.IGNORE);
                        LOGGER.log(Level.WARNING, MessageFormat.format(
                                "WARNING: Invalid message signature received from {0}:{1}",
                                InetAddress.getByName(response.getAddress().getHostName()), response.getPort()));
                        return message;
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException |
                         NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                         BadPaddingException e) {
                    // if we cannot decrypt the message, we ignore the message as it is not valid
                    // it can be either a KeyProposal or a HMACMessage
                    // if it is a KeyProposal and we cannot decrypt it, it probably is because the message is corrupted
                    // or the sender is not using the correct public key to encrypt the message, so there's nothing we can do

                    // if it is a HMACMessage we cannot decrypt something that is not encrypted
                    // so an exception is thrown, and we ignore the message as we do not have the key to check the hmac
                    message.setType(Message.Type.IGNORE);
                    LOGGER.log(Level.WARNING, MessageFormat.format(
                            "WARNING: Error decrypting message received from {0}:{1}",
                            InetAddress.getByName(response.getAddress().getHostName()), response.getPort()));
                    return message;
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
                Key aesKey = new SecretKeySpec(((KeyProposal) message).getKey().getBytes(),
                        0, ((KeyProposal) message).getKey().getBytes().length, "AES");

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
        // setup shared keys between channels
        for (ProcessConfig dest : nodes) {
            if (dest.getId() < self.getId()) {
                // only send key proposal to nodes with higher id
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
                    String serialize = new Gson().toJson(aesKeyString);
                    signature = Optional.of(SigningUtils.sign(serialize, this.config.getPrivateKey()));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new HDSSException(ErrorMessage.SigningError);
                }

                KeyProposal keyProposal = new KeyProposal(this.config.getId(), aesKeyString, signature.get(), dest.getPublicKey());
                keyProposal.setReceiver(dest.getId());
                keyProposal.setMessageId(messageCounter.getAndIncrement());

                apLink.send(dest.getId(), keyProposal);
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.GeneratingKeyError);
            }
        }
    }
}
