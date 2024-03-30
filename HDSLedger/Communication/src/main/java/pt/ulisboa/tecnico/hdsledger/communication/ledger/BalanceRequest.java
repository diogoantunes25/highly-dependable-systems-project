package pt.ulisboa.tecnico.hdsledger.communication.ledger;

public class BalanceRequest {
    private int source;

    public BalanceRequest(int source) {
        this.source = source;
    }

    public int getSource() {
        return source;
    }
}
