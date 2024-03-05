package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class HMACMessage implements Serializable {

    // Message content
    private String message;
    // Message signature
    private String hmac;

    public HMACMessage(String message, String hmac) {
        this.message = message;
        this.hmac = hmac;
    }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public String getHmac() { return hmac; }

    public void setHmac(String hmac) { this.hmac = hmac; }
}
