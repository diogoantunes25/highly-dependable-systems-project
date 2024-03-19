package pt.ulisboa.tecnico.hdsledger.service;

public class Slot<C extends Command> {

    // Slot position in ledger
    private int slotId;

    private C cmd;
    
    public Slot(int slotId, C cmd) {
        this.slotId = slotId;
        this.cmd = cmd;
    }

    public int getSlotId() {
        return slotId;
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
