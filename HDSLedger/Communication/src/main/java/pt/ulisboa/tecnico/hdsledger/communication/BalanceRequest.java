package pt.ulisboa.tecnico.hdsledger.communication;

public class BalanceRequest {

    private int senderId;

    private String sourcePublicKey;

    private int nonce;

    public BalanceRequest(String sourcePublicKey, int nonce) {
        this.sourcePublicKey = sourcePublicKey;
        this.nonce = nonce;
    }

    public String getSourcePublicKey() {
        return sourcePublicKey;
    }

    public void setSourcePublicKey(String sourcePublicKey) {
        this.sourcePublicKey = sourcePublicKey;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }
}
