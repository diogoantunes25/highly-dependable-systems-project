package pt.ulisboa.tecnico.hdsledger.service;

import java.util.Optional;

@FunctionalInterface
public interface ObserverAck {
    public void ack(int senderId, int seq, Optional<Integer> slotId);
}
