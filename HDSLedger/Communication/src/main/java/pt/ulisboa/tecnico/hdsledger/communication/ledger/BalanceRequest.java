package pt.ulisboa.tecnico.hdsledger.communication.ledger;

public class BalanceRequest {

    private int senderId;

    private String sourcePublicKey;

    private int sequenceNumber;

    public BalanceRequest(String sourcePublicKey, int sequenceNumber) {
        this.sourcePublicKey = sourcePublicKey;
        this.sequenceNumber = sequenceNumber;
    }

    public String getSourcePublicKey() {
        return sourcePublicKey;
    }

    public void setSourcePublicKey(String sourcePublicKey) {
        this.sourcePublicKey = sourcePublicKey;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
