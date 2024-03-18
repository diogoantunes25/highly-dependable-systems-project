package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;

public class KeyProposal extends Message implements Serializable {

    private byte[] key;

    private String signature;

    // Who sent the previous message
    private int replyTo;

    // Id of the previous message
    private int replyToMessageId;

    public KeyProposal(int nodeID, Key key, String signature, String receiverPublicKey) {
        super(nodeID, Type.KEY_PROPOSAL);
        this.key = encrypt(key, receiverPublicKey);
        this.signature = signature;
    }

    public byte[] getKey() { return key; }

    public void setKey(byte[] key) { this.key = key; }


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

    public String getSignature() { return signature; }

    public void setSignature(String signature) { this.signature = signature; }

    private byte[] encrypt(Key key, String publicKey) {
        // encrypt message with receiver's public key
        byte[] encryptedData;
        try {
            encryptedData = SigningUtils.encryptWithPublic(key, publicKey);
            return encryptedData;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | NoSuchPaddingException |
                 InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchProviderException e) {
            e.printStackTrace();
            throw new HDSSException(ErrorMessage.EncryptionError);
        }
    }
}
