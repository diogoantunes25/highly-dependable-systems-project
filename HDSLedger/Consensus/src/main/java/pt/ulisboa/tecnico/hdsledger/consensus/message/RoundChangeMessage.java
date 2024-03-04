package pt.ulisboa.tecnico.hdsledger.consensus.message;

import com.google.gson.Gson;

import java.util.Optional;

public class RoundChangeMessage {

    // Prepare value
    private String pvi;
    
    // Prepare round
    private int pri;

    // Whether is bottom
    // Very, very bad. Done because Gson doesn't support Optionals :|
    private boolean present = false;

    public RoundChangeMessage(Optional<String> pvi, Optional<Integer> pri) {
        if (pvi.isPresent()) {
            this.pvi = pvi.get();
            this.pri = pri.get();
            this.present = true;
        }
    }

    public Optional<String> getPvi() {
        if (present) {
            return Optional.of(pvi);
        }
        return Optional.empty();
    }

    public Optional<Integer> getPri() {
        if (present) {
            return Optional.of(pri);
        }
        return Optional.empty();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
