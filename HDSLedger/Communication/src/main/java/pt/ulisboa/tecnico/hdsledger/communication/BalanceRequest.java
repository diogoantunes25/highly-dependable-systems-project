package pt.ulisboa.tecnico.hdsledger.communication;

public class BalanceRequest {
    private String sourcePublicKey;

    public BalanceRequest(String sourcePublicKey) {
        this.sourcePublicKey = sourcePublicKey;
    }

    public String getSourcePublicKey() {
        return sourcePublicKey;
    }

    public void setSourcePublicKey(String sourcePublicKey) {
        this.sourcePublicKey = sourcePublicKey;
    }
}
