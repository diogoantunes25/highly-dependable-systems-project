package pt.ulisboa.tecnico.hdsledger.communication.ledger;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

public class LedgerMessage extends Message {

    // Message (REQUEST, REPLY) - serialized version
    private String message;

    // Signature of current message (with signature = null)
    // The usage is - message is created, then signed and then signature set
    private String signature;

    // Who sent the previous message
    private int replyTo;
    // Id of the previous message
    private int replyToMessageId;

    private int sequenceNumber;

    public LedgerMessage(int senderId, Message.Type type) {
        super(senderId, type);
    }

    public BalanceRequest deserializeBalanceRequest() {
        return new Gson().fromJson(this.message, BalanceRequest.class);
    }

    public BalanceReply deserializeBalanceReply() {
        return new Gson().fromJson(this.message, BalanceReply.class);
    }

    public TransferRequest deserializeTransferRequest() {
        return new Gson().fromJson(this.message, TransferRequest.class);
    }

    public TransferReply deserializeTransferReply() {
        return new Gson().fromJson(this.message, TransferReply.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private LedgerMessage.Signable getToSign() {
        return new LedgerMessage.Signable(this.message);
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

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Signs itself and stores signature
     */
    public void signSelf(String pathToPrivateKey) {
        // serialize myself with null signature
        this.signature = null;

        pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage.Signable toSign = this.getToSign();
        String serialized = new Gson().toJson(toSign);
        System.out.printf("signSelf - signing %s\n", serialized);
        try {
            this.signature = SigningUtils.sign(serialized, pathToPrivateKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException |
                 NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | IOException e) {
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
        private String message;

        Signable(String message) {
            this.message = message;
        }
    }
}
