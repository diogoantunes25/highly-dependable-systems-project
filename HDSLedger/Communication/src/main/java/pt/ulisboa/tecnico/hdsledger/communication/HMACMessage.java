package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.Serializable;

public class HMACMessage extends Message implements Serializable {
    // Message signature
    private String hmac;

    public HMACMessage(int nodeID, Type type, String hmac) {
        super(nodeID, type);
        this.hmac = hmac;
    }

    public String getHmac() { return hmac; }

    public void setHmac(String hmac) { this.hmac = hmac; }
}
