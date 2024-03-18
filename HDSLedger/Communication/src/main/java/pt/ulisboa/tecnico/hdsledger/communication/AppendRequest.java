package pt.ulisboa.tecnico.hdsledger.communication;

public class AppendRequest {
    
    // Value to append
    private String value;

    //nonce
    private int sequenceNumber;

    public AppendRequest(String value, Integer sequenceNumber) {
        this.value = value;
        this.sequenceNumber = sequenceNumber;
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

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
