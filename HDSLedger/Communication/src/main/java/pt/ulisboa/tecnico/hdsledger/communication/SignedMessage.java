package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.Serializable;

public class SignedMessage extends Message implements Serializable {

    // Message content
    private String signature;
    // Message signature
    private String message;

    public SignedMessage(int senderId, Type type, String message, String signature) {
        super(senderId, type);
        this.message = message;
        this.signature = signature;
    }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public String getSignature() { return signature; }

    public void setSignature(String signature) { this.signature = signature; }
}
