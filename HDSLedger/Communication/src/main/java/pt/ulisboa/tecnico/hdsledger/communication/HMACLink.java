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
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Authenticated point to point link.
 */
public class HMACLink implements Link {

    private static final CustomLogger LOGGER = new CustomLogger(APLink.class.getName());
    // Time to wait for an ACK before resending the message
    private final Map<Integer, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Send messages to self by pushing to queue instead of through the network
    private final Map<Integer, Key> sharedKeys = new ConcurrentHashMap<>();
    // APLink reference
    private final APLink apLink;

    public HMACLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this(self, port, nodes, messageClass, false, 200);
    }

    public HMACLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
                    boolean activateLogs, int baseSleepTime) {
        this.apLink = new APLink(self, port, nodes, messageClass, activateLogs, baseSleepTime);
        this.config = self;

        Arrays.stream(nodes).forEach(node -> {
            int id = node.getId();
            this.nodes.put(id, node);
        });

        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
        setupChannelKeys(self, nodes);
    }

    public void ackAll(List<Integer> messageIds) {
        apLink.ackAll(messageIds);
    }

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcast
     */
    public void broadcast(Message data) {
        apLink.broadcast(data);
    }

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    public void send(int nodeId, Message data) {
        if (sharedKeys.get(nodeId) == null) {
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
                sendWhenKeyIsReady(nodeId, data);
            }).start();
        } else {
            sendWhenKeyIsReady(nodeId, data);
        }
    }

    private void sendWhenKeyIsReady(int nodeId, Message data) {
        String dataString = new Gson().toJson(data);
        Key sharedKey = sharedKeys.get(nodeId);
        String hmac = SigningUtils.generateHMAC(dataString, sharedKey);
        HMACMessage hmacMessage = new HMACMessage(data.getSenderId(), data.getType(), hmac);
                /*System.out.println(MessageFormat.format(
                        "Sending message of type {0} to {1}:{2} with message ID {3} -" +
                                "message has data: {4} with HMAC: {5}",
                        hmacMessage.getType(), nodes.get(nodeId).getHostname(),
                        nodes.get(nodeId).getPort(), hmacMessage.getMessageId(),
                        hmacMessage.getMessage(), hmacMessage.getHmac()));*/
        hmacMessage.setReceiver(nodeId);
        apLink.send(nodeId, hmacMessage);
    }
    /*
     * Receives a message from any node in the network (blocking)
     */
    public Message receive() throws IOException, ClassNotFoundException {
        Message message;
        while (true) {
            message = apLink.receiveAndDeserializeWith(HMACMessage.class);
            if (!message.getType().equals(Message.Type.IGNORE)) {
                if (sharedKeys.containsKey(message.getSenderId()) && !message.getType().equals(Type.KEY_PROPOSAL) && !message.getType().equals(Type.ACK)) {
                    System.out.println("PROCESSING HMAC MESSAGE");
                    message = processHMACMessage(message);
                } else if (message.getType().equals(Type.KEY_PROPOSAL)) {  // if we do not have a sharedKey than the message probably is a Key Proposal
                    message = processKeyProposal(message);
                }
                break;
            }
        }

        return message;
    }

    private Message processHMACMessage(Message message) throws UnknownHostException {
        // If we already have the key than it is probably a HMACMessage, it is unlikely that we receive a KeyProposal
        // if it is an HMAC Message, but we do not have the key, we ignore the message as there's nothing we can do
        byte[] buffer = new Gson().toJson(message).getBytes();
        message = new Gson().fromJson(new String(buffer), HMACMessage.class);
        // verify hmac
        String hmac = ((HMACMessage) message).getHmac();
        Message msg = new Message(message.getSenderId(), message.getType());
        String messageString = new Gson().toJson(msg);
        Key sharedKey = sharedKeys.get(message.getSenderId());
        if (!hmac.equals(SigningUtils.generateHMAC(messageString, sharedKey))) {
            // if the hmac is invalid, we ignore the message as it is not valid
            message.setType(Message.Type.IGNORE);
            LOGGER.log(Level.WARNING, MessageFormat.format(
                    "WARNING: Invalid message HMAC received from {0}:{1}",
                    InetAddress.getByName(nodes.get(message.getSenderId()).getHostname()),
                    nodes.get(message.getSenderId()).getPort()));
            return message;
        }
        return message;
    }

    private Message processKeyProposal(Message message) throws UnknownHostException {
        try {
            String encryptedKey = ((KeyProposal) message).getKey();
            String decryptedKey = SigningUtils.decryptWithPrivate(encryptedKey.getBytes(), this.config.getPrivateKey());
            // verify signature
            if (!SigningUtils.verifySignature(decryptedKey,
                    ((KeyProposal) message).getSignature(),
                    this.nodes.get(message.getSenderId()).getPublicKey())) {
                // if the signature is invalid, we ignore the message as it is not valid
                message.setType(Message.Type.IGNORE);
                LOGGER.log(Level.WARNING, MessageFormat.format(
                        "WARNING: Invalid message signature received from {0}:{1}",
                        InetAddress.getByName(nodes.get(message.getSenderId()).getHostname()),
                        nodes.get(message.getSenderId()).getPort()));
                return message;
            }
            Key aesKey = new SecretKeySpec(((KeyProposal) message).getKey().getBytes(),
                    0, ((KeyProposal) message).getKey().getBytes().length, "AES");
            sharedKeys.put(message.getSenderId(), aesKey);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException |
                 NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            // if we cannot decrypt the message, we ignore the message as it is not valid
            // it can be either a KeyProposal or a HMACMessage
            // if it is a KeyProposal and we cannot decrypt it, it probably is because the message is corrupted
            // or the sender is not using the correct public key to encrypt the message, so there's nothing we can do

            // if it is a HMACMessage we cannot decrypt something that is not encrypted
            // so an exception is thrown, and we ignore the message as we do not have the key to check the hmac
            System.out.println("MESSAGE: " + new Gson().toJson(message));
            System.out.println(e);
            LOGGER.log(Level.WARNING, MessageFormat.format(
                    "WARNING: Error decrypting message received from {0}:{1}",
                    InetAddress.getByName(nodes.get(message.getSenderId()).getHostname()),
                    nodes.get(message.getSenderId()).getPort()));
            message.setType(Message.Type.IGNORE);
            return message;
        }
        return message;
    }

    /**
     * Tries to receive a message from any node in the network (non-blocking)
     */
    public Optional<Message> tryReceive() throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException("TODO");
    }

    public void setupChannelKeys(ProcessConfig self, ProcessConfig[] nodes) {
        // setup shared keys between channels
        for (ProcessConfig dest : nodes) {
            if (dest.getId() <= self.getId()) {
                // only send key proposal to nodes with higher id
                continue;
            }

            LOGGER.log(Level.INFO, MessageFormat.format("Sending key proposal to {0}:{1}",
                    dest.getHostname(), dest.getPort()));

            try {
                Key aesKey = SigningUtils.generateSimKey();
                // store key in sharedKeys
                sharedKeys.put(dest.getId(), aesKey);

                String aesKeyString = Base64.getEncoder().encodeToString(aesKey.getEncoded());
                Optional<String> signature;
                // Sign message
                try {
                    // serialize keyProposal
                    signature = Optional.of(SigningUtils.sign(aesKeyString, this.config.getPrivateKey()));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new HDSSException(ErrorMessage.SigningError);
                }

                KeyProposal keyProposal = new KeyProposal(this.config.getId(), aesKeyString, signature.get(), dest.getPublicKey());
                keyProposal.setReceiver(dest.getId());
                /*System.out.println(MessageFormat.format(
                        "Sending key proposal to {0}:{1} with key: {2} and signature: {3}",
                        dest.getHostname(), dest.getPort(), keyProposal.getKey(), keyProposal.getSignature()));*/
                apLink.send(dest.getId(), keyProposal);
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.GeneratingKeyError);
            }
        }
    }
}
