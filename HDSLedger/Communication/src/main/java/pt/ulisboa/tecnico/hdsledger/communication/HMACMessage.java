package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.Serializable;

public class HMACMessage extends Message implements Serializable {
    // Message signature
    private byte[] hmac;

    public HMACMessage(int nodeID, Type type, byte[] hmac) {
        super(nodeID, type);
        this.hmac = hmac;
    }

    public byte[] getHmac() { return hmac; }

    public void setHmac(byte[] hmac) { this.hmac = hmac; }
}
