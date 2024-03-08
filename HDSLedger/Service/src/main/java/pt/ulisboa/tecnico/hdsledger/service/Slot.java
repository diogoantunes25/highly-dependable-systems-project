package pt.ulisboa.tecnico.hdsledger.service;

public class Slot {

    // Slot position in ledger
    private int slotId;

    // Sequence number provided by client
    private int seq;

    // Client that proposed command
    private int clientId;

    // State machine command / string to append
    private String message;

    public Slot(int slotId, int seq, int clientId, String message) {
        this.slotId = slotId;
        this.seq = seq;
        this.clientId = clientId;
        this.message = message;
    }

    public int getSlotId() {
        return slotId;
    }

    public int getClientId() {
        return clientId;
    }

    public int getSeq() {
        return seq;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "Slot{" +
                "slotId=" + slotId +
                ", seq='" + seq + '\'' +
                ", clientId='" + clientId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
