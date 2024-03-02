package pt.ulisboa.tecnico.hdsledger.communication;

public class AppendRequest extends Message {
    
    // Value to append
    private String value;

    //nonce
    private Integer nonce;

    public AppendRequest(String senderId, Type type, String value, Integer nonce) {
        super(senderId, type);
        this.value = value;
        this.nonce = nonce;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }
}
