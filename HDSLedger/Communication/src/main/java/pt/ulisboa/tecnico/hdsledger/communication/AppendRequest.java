package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.*;

public class AppendRequest extends Message {
    
    // Value to append
    private String value;

    //nonce
    private Integer sequenceNumber;

    public AppendRequest(int senderId, Type type, String value, Integer sequenceNumber) {
        super(senderId, type);
        this.value = value;
        this.sequenceNumber = sequenceNumber;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
