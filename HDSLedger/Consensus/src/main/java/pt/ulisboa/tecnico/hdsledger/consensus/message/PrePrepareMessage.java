package pt.ulisboa.tecnico.hdsledger.consensus.message;

import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;

public class PrePrepareMessage {
    
    // Value
    private String value;

    // ROUND-CHANGE messages part of justification
    private List<ConsensusMessage> justificationRoundChanges = null;

    // PREPARE messages part of justification (no duplicates should exist
    // otherwise QBFT goes O(n3))
    private List<ConsensusMessage> justificationPrepares = null;

    public PrePrepareMessage(String value, Optional<List<ConsensusMessage>> justificationPrepares, Optional<List<ConsensusMessage>> justificationRoundChanges) {
        this.value = value;

        if (justificationPrepares.isPresent()) {
            this.justificationRoundChanges = justificationRoundChanges.get();
            this.justificationPrepares = justificationPrepares.get();
        }
    }

    public String getValue() {
        return value;
    }

    public Optional<List<ConsensusMessage>> getJustificationPrepares() {
        if (justificationPrepares == null) {
            return Optional.empty();
        }

        return Optional.of(justificationPrepares);
    }

    public Optional<List<ConsensusMessage>> getJustificationRoundChanges() {
        if (justificationRoundChanges == null) {
            return Optional.empty();
        }

        return Optional.of(justificationRoundChanges);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}   
