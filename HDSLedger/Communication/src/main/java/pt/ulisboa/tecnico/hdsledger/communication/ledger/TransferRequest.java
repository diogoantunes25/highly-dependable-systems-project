package pt.ulisboa.tecnico.hdsledger.communication.ledger;

public class TransferRequest {

    private int source;

    private int destination;

    private int amount;

    public TransferRequest(int source, int destination, int amount) {
        this.source = source;
        this.destination = destination;
        this.amount = amount;
    }

    public int getSource() {
        return source;
    }

    public int getDestination() {
        return destination;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
