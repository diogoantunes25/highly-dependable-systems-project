package pt.ulisboa.tecnico.hdsledger.service;

import java.util.Optional;

public class Slot<C extends Command> {

    // Slot position in ledger
    private Optional<Integer> slotId;

    private C cmd;
    
    public Slot(Optional<Integer> slotId, C cmd) {
        this.slotId = slotId;
        this.cmd = cmd;
    }

    public Optional<Integer> getSlotId() {
        return slotId;
    }

    public boolean failed() {
        return this.slotId.isEmpty();
    }

    public C getCmd() {
        return cmd;
    }

    @Override
    public String toString() {
        return "Slot{" +
                "slotId=" + slotId +
                ", command='" + cmd + '\'' +
                '}';
    }
}
