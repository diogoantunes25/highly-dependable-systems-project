package pt.ulisboa.tecnico.hdsledger.communication;

public class TransferRequest {

    private int senderId;

    private String sourcePublicKey;

    private String destinationPublicKey;

    private int amount;

    private int tip;

    private int nonce;

    public TransferRequest(String sourcePublicKey, String destinationPublicKey, int amount, int tip, int nonce) {
        this.sourcePublicKey = sourcePublicKey;
        this.destinationPublicKey = destinationPublicKey;
        this.amount = amount;
        this.tip = tip;
        this.nonce = nonce;
    }

    public String getSourcePublicKey() {
        return sourcePublicKey;
    }

    public String getDestinationPublicKey() {
        return destinationPublicKey;
    }

    public int getAmount() {
        return amount;
    }

    public int getTip() {
        return tip;
    }

    public void setSourcePublicKey(String sourcePublicKey) {
        this.sourcePublicKey = sourcePublicKey;
    }

    public void setDestinationPublicKey(String destinationPublicKey) {
        this.destinationPublicKey = destinationPublicKey;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void setTip(int tip) {
        this.tip = tip;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }
}
