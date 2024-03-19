package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendRequest;

import java.util.Optional;
import java.util.Objects;

import com.google.gson.Gson;

/**
 * Command for state machine
 */
public class StringCommand implements Command {

    int clientId;
    int seq;
    String value;
    String serializedProof;

    public StringCommand(int clientId, int seq, String value, AppendMessage proof) {
        // TODO: check that clientId, seq and value are consistent with proof
        // provided
        
        AppendRequest appendRequest = proof.deserializeAppendRequest();
        if (proof.getSenderId() != clientId ||
            appendRequest.getSequenceNumber() != seq ||
            !appendRequest.getValue().equals(value)) {
            // TODO : move to HDSLedgerException
            throw new RuntimeException("Bad command provided - proof is not consistent with values provided");
        }

        this.clientId = clientId;
        this.seq = seq;
        this.value = value;
        this.serializedProof = new Gson().toJson(proof);
    }

    public String serialize() {
        return new Gson().toJson(this);
    }

    public static Optional<StringCommand> deserialize(String blob) {
        // TODO: check if successful
        // TODO: check that serialized proof actually deserializes into correct
        // thing
        return Optional.of(new Gson().fromJson(blob, StringCommand.class));
    }

    public int getClientId() {
        return this.clientId;
    }

    public int getSeq() {
        return this.seq;
    }

    public String getValue() {
        return this.value;
    }

    public AppendMessage getProof() {
        return new Gson().fromJson(this.serializedProof, AppendMessage.class);
    }

    /* Default method for equality */ 
    public boolean equalsWithoutProof(StringCommand other) {
        return (this.clientId == other.getClientId()) &&
            (this.seq == other.getSeq()) &&
            this.value.equals(other.getValue());
    }

    public boolean equalsWithProof(StringCommand other) {
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

        StringCommand other = (StringCommand) o;
        return this.equalsWithoutProof(other);
    }

    /*
     * Hash code that doesn't consider proof value
     */
    @Override
    public int hashCode() {
        return Objects.hash(clientId, seq, value);
    }

    @Override
    public String toString() {
        return "StringCommand{" +
                "clientId=" + clientId +
                ", seq=" + seq +
                ", value='" + value + '\'' +
                ", serializedProof='" + serializedProof + '\'' +
                '}';
    }
}
