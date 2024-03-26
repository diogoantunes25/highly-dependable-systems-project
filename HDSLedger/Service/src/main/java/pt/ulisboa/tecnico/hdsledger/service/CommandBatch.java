package pt.ulisboa.tecnico.hdsledger.service;

import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;

public class CommandBatch implements Command {

    List<BankCommand> commands;

    public CommandBatch(List<BankCommand> commands) {
        this.commands = commands;
    }

    public String serialize() {
        return new Gson().toJson(this);
    }

    public List<BankCommand> getCommands() {
        return this.commands;
    }

    public static Optional<CommandBatch> deserialize(String blob) {
        // TODO: check if successful
        // TODO: check that serialized proof actually deserializes into correct
        // thing
        return Optional.of(new Gson().fromJson(blob, CommandBatch.class));
    }

    /*
     * Check validity of signatures and fees
     **/
    public boolean check(List<String> keys, int expectedFee) {
        return this.commands.stream().allMatch(cmd ->
                cmd.checkSig(keys.get(cmd.getClientId())) &&
                    cmd.getFee() == expectedFee);
    }
}
