package pt.ulisboa.tecnico.hdsledger.consensus.message;

import java.util.Optional;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

public class ConsensusMessage extends Message {

    // Consensus instance
    private int consensusInstance;
    // Round
    private int round;
    // Who sent the previous message
    private int replyTo;
    // Id of the previous message
    private int replyToMessageId;
    // Message (PREPREPARE, PREPARE, COMMIT)
    private String message;

    // Signature of current message (with signature = null)
    // The usage is - message is created, then signed and then signature set
    private String signature;

    public ConsensusMessage(int senderId, Type type) {
        super(senderId, type);
    }

    public PrePrepareMessage deserializePrePrepareMessage() {
        return new Gson().fromJson(this.message, PrePrepareMessage.class);
    }

    public PrepareMessage deserializePrepareMessage() {
        return new Gson().fromJson(this.message, PrepareMessage.class);
    }

    public CommitMessage deserializeCommitMessage() {
        return new Gson().fromJson(this.message, CommitMessage.class);
    }

    public RoundChangeMessage deserializeRoundChangeMessage() {
        return new Gson().fromJson(this.message, RoundChangeMessage.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(int replyTo) {
        this.replyTo = replyTo;
    }

    public int getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(int replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    /**
     * Signs itself and stores signature
     */
    public void signSelf(String pathToPrivateKey) {
        // serialize myself with null signature
        this.signature = null;
        String serialized = new Gson().toJson(this);
        try {
            this.signature = SigningUtils.sign(serialized, pathToPrivateKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException |
            NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | IOException  e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Checks if signature matches message content
     * @param pathToPublicKey path to public key of sender
     *
     * Not thread-safe.
     **/
    public boolean checkConsistentSig(String pathToPublicKey) {
        if (this.signature == null) {
            return false;
        }

        // Get state at time of signing
        String tmp = this.signature;
        this.signature = null;

        // Check signature
        String serialized = new Gson().toJson(this);
        boolean result = SigningUtils.verifySignature(serialized, tmp, pathToPublicKey);

        // Restore original state
        this.signature = tmp;

        return result;
    }

    public Optional<String> getSignature() {
        if (this.signature == null) {
            Optional.empty();
        }
        return Optional.of(this.signature);
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
