package pt.ulisboa.tecnico.hdsledger.communication.ledger;

public class BalanceReply {
    // Value to append
    private int value;

    // nonce
    private int sequenceNumber;
    public BalanceReply(int value, int sequenceNumber) {
        this.value = value;
        this.sequenceNumber = sequenceNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
