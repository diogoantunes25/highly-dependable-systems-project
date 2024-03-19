package pt.ulisboa.tecnico.hdsledger.service;

import java.util.Optional;

public interface State<C extends Command> {

    /**
     * Does not check client signatures.
     */
    public boolean isValidCommand(C cmd);

    /**
     * Update current state. Only executes if command is valid
     * @return operation id, or empty if failed
     */
    public Optional<Integer> update(C cmd);
}
