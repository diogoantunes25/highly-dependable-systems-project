package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

public class TransferMessage extends Message {

    // Message (REQUEST, REPLY) - serialized version
    private String message;

    // Signature of current message (with signature = null)
    // The usage is - message is created, then signed and then signature set
    private String signature;

    // Who sent the previous message
    private int replyTo;
    // Id of the previous message
    private int replyToMessageId;

    public TransferMessage(int senderId, Message.Type type) {
        super(senderId, type);
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

    private TransferMessage.Signable getToSign() {
        return new TransferMessage.Signable(this.message);
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

        TransferMessage.Signable toSign = this.getToSign();
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
