package pt.ulisboa.tecnico.hdsledger.communication.ledger;

import java.util.Optional;

public class BalanceReply {
    // Value to append
    private int value;

    private boolean success;

    // sequence number
    private int sequenceNumber;

    public BalanceReply(Optional<Integer> value, int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
        this.success = value.isPresent();
        this.value = value.isPresent() ? value.get() : 0;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Optional<Integer> getValue() {
        if (success) { return Optional.of(value); }
        return Optional.empty();
    }

    public void setValue(int value) {
        this.value = value;
    }
}
