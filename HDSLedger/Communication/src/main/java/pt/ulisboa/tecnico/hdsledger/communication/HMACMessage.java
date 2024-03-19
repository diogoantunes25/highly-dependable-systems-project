package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class HMACMessage extends Message implements Serializable {
    // Message hmac
    private byte[] hmac;

    // String serialized message
    private String message;

    // Who sent the previous message
    private int replyTo;

    // Id of the previous message
    private int replyToMessageId;


    public HMACMessage(int nodeID, Type type, byte[] hmac, String message) {
        super(nodeID, type);
        this.hmac = hmac;
        this.message = message;
    }

    public byte[] getHmac() { return hmac; }

    public void setHmac(byte[] hmac) { this.hmac = hmac; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

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


}
