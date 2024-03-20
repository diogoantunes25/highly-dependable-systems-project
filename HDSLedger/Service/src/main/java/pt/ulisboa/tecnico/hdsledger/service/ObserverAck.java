package pt.ulisboa.tecnico.hdsledger.service;

@FunctionalInterface
public interface ObserverAck {
    public void ack(int senderId, int seq, int slotId);
}
