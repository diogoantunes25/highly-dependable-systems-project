package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
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
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class KeyProposal extends Message implements Serializable {

    private String key;

    private String signature;

    public KeyProposal(int nodeID, String key, String signature, String receiverPublicKey) {
        super(nodeID, Type.KEY_PROPOSAL);
        this.key = encrypt(key, receiverPublicKey);
        this.signature = encrypt(signature, receiverPublicKey);
    }

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }

    public String getSignature() { return signature; }

    public void setSignature(String signature) { this.signature = signature; }

    private String encrypt(String message, String publicKey) {
        // encrypt message with receiver's public key
        String encryptedData;
        try {
            encryptedData = SigningUtils.encrypt(message.getBytes(), publicKey);
            return encryptedData;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException |
                 NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            e.printStackTrace();
            throw new HDSSException(ErrorMessage.EncryptionError);
        }
    }
}
