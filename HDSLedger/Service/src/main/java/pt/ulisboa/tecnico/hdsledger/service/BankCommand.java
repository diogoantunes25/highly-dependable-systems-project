package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import java.util.Optional;
import java.util.Objects;

import com.google.gson.Gson;

/**
 * Command for state machine
 */
public class BankCommand implements Command {

    private int seq;
    private String source;
    private String destination;
    private int amount;
    private String serializedProof;

    public BankCommand(int seq, String source, String destination, int amount, LedgerMessage proof) {

        TransferRequest transferRequest = proof.deserializeTransferRequest();
        String messageSource = SigningUtils.publicKeyHash(transferRequest.getSourcePublicKey());
        String messageDestination = SigningUtils.publicKeyHash(transferRequest.getDestinationPublicKey());

        if (seq != transferRequest.getSequenceNumber() &&
                !source.equals(messageSource) &&
                !destination.equals(messageDestination) &&
                amount != transferRequest.getAmount()) {
            // TODO: move to HDSLedgerException
            throw new RuntimeException("Bad command provided - proof is not consistent with values provided");
        }

        this.seq = seq;
        this.source = source;
        this.destination = destination;
        this.amount = amount;
        this.serializedProof = new Gson().toJson(proof);
    }

    public String serialize() {
        return new Gson().toJson(this);
    }

    public static Optional<BankCommand> deserialize(String blob) {
        // TODO: check if successful
        // TODO: check that serialized proof actually deserializes into correct
        // thing
        return Optional.of(new Gson().fromJson(blob, BankCommand.class));
    }

    public int getSeq() { return this.seq; }

    public String getSource() { return this.source; }

    public String getDestination() { return this.destination; }
    
    public int getAmount() { return this.amount; }

    public LedgerMessage getProof() {
        return new Gson().fromJson(this.serializedProof, LedgerMessage.class);
    }
}
