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

    private Signable getToSign() {
        return new Signable(this.consensusInstance, this.round, this.message);
    }

    /**
     * Signs itself and stores signature
     */
    public void signSelf(String pathToPrivateKey) {
        // serialize myself with null signature
        this.signature = null;

        Signable toSign = this.getToSign();
        String serialized = new Gson().toJson(toSign);
        System.out.printf("signSelf - signing %s\n", serialized); // switch to logger
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
            System.out.println("checkConsistentSig - bad signature because is null");
            return false;
        }

        // Check signature
        String serialized = new Gson().toJson(this.getToSign());
        System.out.printf("checkConsistentSig - checking signature for %s\n", serialized);
        boolean result = SigningUtils.verifySignature(serialized, this.signature, pathToPublicKey);

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

    // Class that encapsulates what is important to be signed
    private class Signable {
        private int lambda;

        private int round;

        private String message;

        Signable(int lambda, int round, String message) {
            this.lambda = lambda;
            this.round = round;
            this.message = message;
        }
    }
}
