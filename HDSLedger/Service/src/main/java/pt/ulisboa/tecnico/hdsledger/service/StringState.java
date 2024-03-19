package pt.ulisboa.tecnico.hdsledger.service;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Not thread-safe
 */
public class StringState implements State<StringCommand> {

    private List<StringCommand> ledger;
    private int ops;

    public StringState() {
        this.ledger = new ArrayList<>();
        this.ops = 0;
    }

    public boolean isValidCommand(StringCommand cmd) {
        return true;
    }

    public Optional<Integer> update(StringCommand cmd) {
        if (!this.isValidCommand(cmd)) {
            return Optional.empty();
        }

        this.forceUpdate(cmd);
        this.ops++;

        return Optional.of(this.ops);
    }

    /**
     * Update current state.
     */
    private void forceUpdate(StringCommand cmd) {
        // TODO (dsa): explain this better
        // Store entire command, which allows for resending and better
        // accountability
        ledger.add(cmd);
    }

    public List<String> getState() {
        return ledger.stream()
            .map(cmd -> cmd.getValue())
            .collect(Collectors.toList());
    }
}
