package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.Serializable;

public class KeyProposal extends Message implements Serializable {

    private String key;

    private String signature;

    public KeyProposal(int nodeID, String key, String signature) {
        super(nodeID, Type.KEY_PROPOSAL);
        this.key = key;
        this.signature = signature;
    }

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }

    public String getSignature() { return signature; }

    public void setSignature(String signature) { this.signature = signature; }
}
