package pt.ulisboa.tecnico.hdsledger.communication.consensus.builder;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;

public class ConsensusMessageBuilder {
    private final ConsensusMessage instance;

    public ConsensusMessageBuilder(int sender, Message.Type type) {
        instance = new ConsensusMessage(sender, type);
    }

    public ConsensusMessageBuilder setMessage(String message) {
        instance.setMessage(message);
        return this;
    }

    public ConsensusMessageBuilder setConsensusInstance(int consensusInstance) {
        instance.setConsensusInstance(consensusInstance);
        return this;
    }

   public ConsensusMessageBuilder setRound(int round) {
        instance.setRound(round);
        return this;
   }

   public ConsensusMessageBuilder setReplyTo(int replyTo) {
        instance.setReplyTo(replyTo);
        return this;
   }

    public ConsensusMessageBuilder setReplyToMessageId(int replyToMessageId) {
        instance.setReplyToMessageId(replyToMessageId);
        return this;
    }

    public ConsensusMessageBuilder setReceiver(int receiver) {
        instance.setReceiver(receiver);
        return this;
    }

    public ConsensusMessage build() {
        return instance;
    }
}
