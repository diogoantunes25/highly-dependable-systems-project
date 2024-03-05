package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.Serializable;

public class KeyProposal extends Message implements Serializable {

    private String nodeID;

    private String key;

    public KeyProposal(String nodeID, String key) {
        super(Integer.parseInt(nodeID), Type.KEY_PROPOSAL);
        this.nodeID = nodeID;
        this.key = key;
    }

    public String getnodeID() { return nodeID; }

    public void setnodeID(String nodeID) { this.nodeID = nodeID; }

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }
}
