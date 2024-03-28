package pt.ulisboa.tecnico.hdsledger.communication.ledger;

public class BalanceRequest {
    private int source;

    // sequence number
    private int seq;

    public BalanceRequest(int seq, int source) {
        this.source = source;
        this.seq = seq;
    }

    public int getSeq() {
        return this.seq;
    }

    public int getSource() {
        return source;
    }
}
