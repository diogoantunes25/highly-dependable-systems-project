package pt.ulisboa.tecnico.hdsledger.service;

public class Slot {

    // Slot position in ledger
    private int slotId;

    // Nonce provided to ensure uniqueness
    private String nonce;

    // State machine command / string to append
    private String message;

    public Slot(int slotId, String nonce, String message) {
        this.slotId = slotId;
        this.nonce = nonce;
        this.message = message;
    }

    public int getSlotId() {
        return slotId;
    }

    public String getNonce() {
        return nonce;
    }

    public String getMessage() {
        return message;
    }
}
