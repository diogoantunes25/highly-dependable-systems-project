package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.*;

public class AppendReply extends Message {
    
    // Value to append
    private String value;

    //nonce
    private Integer sequenceNumber;

    //Slot
    private Integer slot;

    public AppendReply(int senderId, Type type, String value, Integer sequenceNumber, Integer slot) {
        super(senderId, type);
        this.value = value;
        this.sequenceNumber = sequenceNumber;
        this.slot = slot;
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

    public Integer getSlot() {
        return slot;
    }

    public void setSlot(Integer slot) {
        this.slot = slot;
    }
}
