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

    private int clientId;
    private int seq;
    private String source;
    private String destination;
    private int amount;
    private String serializedProof;

    /**
     * @param seq Sequence number sent by client that uniquely identifies transaction
     * (for that client)
     * @param source source id (hash of public key)
     * @param destination destination id (hash of public key)
     * @param amount amount of funds to transfer
     * @param proof message proving that transfer was requested by the source
     */
    public BankCommand(int clientId, int seq, String source, String destination, int amount, LedgerMessage proof) {

        TransferRequest transferRequest = proof.deserializeTransferRequest();
        String messageSource = SigningUtils.publicKeyHash(transferRequest.getSourcePublicKey());
        String messageDestination = SigningUtils.publicKeyHash(transferRequest.getDestinationPublicKey());

        if (seq != proof.getSequenceNumber() &&
                !source.equals(messageSource) &&
                !destination.equals(messageDestination) &&
                amount != transferRequest.getAmount()) {
            // TODO: move to HDSLedgerException
            throw new RuntimeException("Bad command provided - proof is not consistent with values provided");
        }

        this.clientId = clientId;
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

    public int getClientId() { return this.clientId; }

    public int getSeq() { return this.seq; }

    public String getSource() { return this.source; }

    public String getDestination() { return this.destination; }
    
    public int getAmount() { return this.amount; }

    public LedgerMessage getProof() {
        return new Gson().fromJson(this.serializedProof, LedgerMessage.class);
    }

    /* Default method for equality */ 
    public boolean equalsWithoutProof(BankCommand other) {
        return (this.seq == other.getSeq()) &&
            (this.source.equals(other.getSource())) &&
            (this.destination.equals(other.getDestination())) &&
            (this.amount == other.getAmount());
    }

    public boolean equalsWithProof(BankCommand other) {
        return this.equalsWithoutProof(other) &&
            this.serializedProof.equals(other.serializedProof);
    }

    /*
     * Comparision that doesn't consider proof value
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BankCommand other = (BankCommand) o;
        return this.equalsWithoutProof(other);
    }

    /*
     * Hash code that doesn't consider proof value
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.seq, this.source, this.destination, this.amount);
    }

    @Override
    public String toString() {
        return "BankCommand{" +
                "seq=" + seq +
                ", source='" + source + '\'' +
                ", destination='" + destination + '\'' +
                ", amount=" + amount +
                ", serializedProof='" + serializedProof + '\'' +
                '}';
    }
}
