package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferReply;

import java.util.List;
import java.util.Optional;

public class MessageCreator {

    public static ConsensusMessage createPrepareMessage(int id, String value, int instance, int round, int receiver) {
        PrepareMessage prepareMessage = new PrepareMessage(value);

        return new ConsensusMessageBuilder(id, Message.Type.PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReceiver(receiver)
                .build();
    }

    public static ConsensusMessage createCommitMessage(int id, String value, int instance, int round, int receiver) {
        CommitMessage commitMessage = new CommitMessage(value);

        return new ConsensusMessageBuilder(id, Message.Type.COMMIT)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(commitMessage.toJson())
                .setReceiver(receiver)
                .build();
    }

    public static ConsensusMessage createRoundChangeMessage(int id, int instance, int round, int receiver, Optional<String> pvi, Optional<Integer> pri, Optional<List<ConsensusMessage>> justification) {
        RoundChangeMessage roundChangeMessage = new RoundChangeMessage(pvi, pri, justification);

        return new ConsensusMessageBuilder(id, Message.Type.ROUND_CHANGE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(roundChangeMessage.toJson())
                .setReceiver(receiver)
                .build();
    }

    public static LedgerMessage createTransferRequest(int requestId, int source, int destination, int amount) {
        // TODO: make this consistent with the way this was done before
        // String sourcePublicKey = String.format("/tmp/pub_%d.key", source);
        // String destinationPublicKey = String.format("/tmp/pub_%d.key", destination);
        TransferRequest transferRequest = new TransferRequest(source, destination, amount);

        LedgerMessage ledgerMessage = new LedgerMessage(source, Message.Type.TRANSFER_REQUEST);
        ledgerMessage.setMessage(new Gson().toJson(transferRequest));
        ledgerMessage.setSequenceNumber(requestId);
        ledgerMessage.signSelf(String.format("/tmp/priv_%d.key", source));

        return ledgerMessage;
    }

    public static LedgerMessage createTransferReply(int source ,int seq, Optional<Integer> slotId) {
        TransferReply transferReply = new TransferReply(seq, slotId);
        LedgerMessage ledgerMessage = new LedgerMessage(source, Message.Type.TRANSFER_REPLY);
        ledgerMessage.setMessage(new Gson().toJson(transferReply));
        return ledgerMessage;
    }


    public static AppendMessage createAppendRequestMessage(int id, int receiver, String value, int sequenceNumber) {
        AppendRequest appendRequest = new AppendRequest(value, sequenceNumber);

        AppendMessage message = new AppendMessage(id, Message.Type.APPEND_REQUEST, receiver);

        message.setMessage(new Gson().toJson(appendRequest));
        message.signSelf(String.format("/tmp/priv_%d.key", id));

        return message;
    }
}
