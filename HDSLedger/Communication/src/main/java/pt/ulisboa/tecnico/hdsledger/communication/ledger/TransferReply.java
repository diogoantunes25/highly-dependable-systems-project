package pt.ulisboa.tecnico.hdsledger.communication.ledger;

import java.util.Optional;

public class TransferReply {
    private int sequenceNumber;
    private int slot;
    private boolean success;

    public TransferReply(int sequenceNumber, Optional<Integer> slot) {
        this.sequenceNumber = sequenceNumber;
        this.slot = slot.isPresent() ? slot.get() : 0;
        this.success = slot.isPresent();
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Optional<Integer> getSlot() {
        if (this.success) {
            return Optional.of(slot);
        }
        return Optional.empty();
    }
}
