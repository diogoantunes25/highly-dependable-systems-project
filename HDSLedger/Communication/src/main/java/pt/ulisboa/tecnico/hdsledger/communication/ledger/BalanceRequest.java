package pt.ulisboa.tecnico.hdsledger.communication.ledger;

public class BalanceRequest {
    private String sourcePublicKey;

    // sequence number
    private int seq;

    public BalanceRequest(int seq, String sourcePublicKey) {
        this.sourcePublicKey = sourcePublicKey;
        this.seq = seq;
    }

    public int getSeq() {
        return this.seq;
    }

    public String getSourcePublicKey() {
        return sourcePublicKey;
    }

    public void setSourcePublicKey(String sourcePublicKey) {
        this.sourcePublicKey = sourcePublicKey;
    }
}
