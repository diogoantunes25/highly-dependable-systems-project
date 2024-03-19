package pt.ulisboa.tecnico.hdsledger.service;

public interface Command {
    public String serialize();
    // Don't have serialize because it would be static
    // TODO (dsa): this interface might be useless
}
