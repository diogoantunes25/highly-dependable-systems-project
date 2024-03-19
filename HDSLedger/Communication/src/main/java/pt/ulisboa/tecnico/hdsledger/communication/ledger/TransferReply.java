package pt.ulisboa.tecnico.hdsledger.communication.ledger;

public class TransferReply {
    // Value to append
    private boolean success;

    //nonce
    private int sequenceNumber;

    private int slot;

    public TransferReply(boolean success, int sequenceNumber, int slot) {
        this.success = success;
        this.sequenceNumber = sequenceNumber;
        this.slot = slot;
    }

    public boolean getSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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
}
