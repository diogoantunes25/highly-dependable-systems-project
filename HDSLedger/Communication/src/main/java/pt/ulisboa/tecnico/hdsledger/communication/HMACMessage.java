package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.Serializable;

public class HMACMessage extends Message implements Serializable {
    // Message hmac
    private byte[] hmac;

    // String serialized message
    private String message;

    public HMACMessage(int nodeID, Type type, byte[] hmac, String message) {
        super(nodeID, type);
        this.hmac = hmac;
        this.message = message;
    }

    public byte[] getHmac() { return hmac; }

    public void setHmac(byte[] hmac) { this.hmac = hmac; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }
}
