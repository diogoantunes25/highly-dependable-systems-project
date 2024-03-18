package pt.ulisboa.tecnico.hdsledger.consensus.message;

import java.io.Serializable;

public class Message implements Serializable {

    // Sender identifier
    private int senderId;

    // Receiver identifier
    private int receiverId;

    // Message identifier
    private int messageId;

    // Message type
    private Type type;

    public enum Type {
        APPEND_REQUEST, APPEND_REPLY, PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE, ACK, IGNORE, KEY_PROPOSAL, HMAC
    }

    public Message(int senderId, Type type) {
        this.senderId = senderId;
        this.type = type;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public int getReceiver() {
        return receiverId;
    }

    public void setReceiver(int receiverId) {
        this.receiverId = receiverId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}
