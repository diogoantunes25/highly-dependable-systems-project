package pt.ulisboa.tecnico.hdsledger.communication;

public class AppendReply {
    
    // Value to append
    private String value;

    //nonce
    private int sequenceNumber;

    //Slot
    private int slot;

    public AppendReply(String value, int sequenceNumber, int slot) {
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

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }
}
