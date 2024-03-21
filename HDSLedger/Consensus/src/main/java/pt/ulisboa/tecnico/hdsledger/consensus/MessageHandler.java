package pt.ulisboa.tecnico.hdsledger.consensus;

import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;

import java.util.List;

@FunctionalInterface
public interface MessageHandler {
    List<ConsensusMessage> handle(ConsensusMessage message);
}
