package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.Serializable;

public class HMACMessage extends Message implements Serializable {

    // Message content
    private String message;
    // Message signature
    private String hmac;

    public HMACMessage(int nodeID, Type type, String message, String hmac) {
        super(nodeID, type);
        this.message = message;
        this.hmac = hmac;
    }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public String getHmac() { return hmac; }

    public void setHmac(String hmac) { this.hmac = hmac; }
}
