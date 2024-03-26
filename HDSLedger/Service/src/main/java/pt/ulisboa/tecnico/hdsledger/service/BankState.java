package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.text.MessageFormat;
import java.util.logging.Level;

import javafx.util.Pair;

/**
 * Not thread-safe
 */
public class BankState implements State<BankCommand> {

    private static final CustomLogger LOGGER = new CustomLogger(BankState.class.getName());

    // number of executed operations
    private int ops;

    /* balance map */
    private HashMap<String, Integer> balances;

    // TODO (dsa): merkle tree type thing (it seems that this will grow quite a lot)?
    /* all transactions (include proofs) - required to ensure non-repudiation */
    private List<BankCommand> history;

    // executed commands for each client (ensures that there's no replay)
    // (`HashSet` is used to have clonable set)
    private Map<String, HashSet<Integer>> executed;

    public BankState() {
        this.balances = new HashMap<>();
        this.history = new ArrayList<>();
        this.executed = new HashMap<>();
        this.ops = 0;
    }

    // Used to bootstrap the system
    // Should not be used after system boots
    // @param owner identification of owner
    public void spawnMoney(String owner, int amount) {
        balances.put(owner, balances.getOrDefault(owner, 0) + amount);
    }

    public static boolean canExecute(Map<String, Integer> record, Map<String, HashSet<Integer>> executed, int seq, String from, int amount) {
        return !executed.getOrDefault(from, new HashSet<>()).contains(seq) && record.getOrDefault(from, 0) >= amount;
    }

    // checks command has not yet been executed and that source has enough funds
    // dangerous to use if not immediatly followed by an update ( because the
    // state might go invalid in between) -> FIXME: make this method private
    public boolean isValidCommand(BankCommand cmd) {
        return canExecute(this.balances, this.executed,
                    cmd.getSeq(), cmd.getSource(), cmd.getAmount() + cmd.getFee());
    }

    private void forceUpdate(CommandBatch cmds) {
        cmds.getCommands().forEach(this::forceUpdate);
    }

    private static void transfer(Map<String, Integer> record, String from, String to, int amount) {
        record.put(from, record.getOrDefault(from, 0) - amount);
        record.put(to, record.getOrDefault(to, 0) + amount);
    }

    // Assumes command is valied
    private void forceUpdate(BankCommand cmd) {
        int realAmount = cmd.getAmount() + cmd.getFee();
        if (balances.getOrDefault(cmd.getSource(), 0) - realAmount < 0) {
            // TODO: replace with HDSLedgerException
            throw new RuntimeException("invalid update was attempted");
        }

        if (executed.getOrDefault(cmd.getSource(), new HashSet<>()).contains(cmd.getSeq())) {
            // TODO: replace with HDSLedgerException
            throw new RuntimeException("replay attack was attempted");
        }

        LOGGER.log(Level.INFO, MessageFormat.format("Transfering {0} from {1} to {2}",
                    cmd.getAmount(), cmd.getSource(), cmd.getDestination()));

        transfer(this.balances, cmd.getSource(), cmd.getDestination(), cmd.getAmount());
        transfer(this.balances, cmd.getSource(), cmd.getMiner(), cmd.getFee());
        this.history.add(cmd);
    }

    /*
     * Returns a pair with the list of valid transctions and the list of invalid
     * transactions
     **/
    public Pair<List<BankCommand>, List<BankCommand>> getValidFromBatch(List<BankCommand> cmds) {
        // Validity is checked by trying to play the transfers in a copy of the
        // state
        
        List<BankCommand> valid = new ArrayList<>();
        List<BankCommand> invalid = new ArrayList<>();

        // Deep copy of balances and executed
        Map<String, Integer> balancesClone = (HashMap<String, Integer>) this.balances.clone();
        Map<String, HashSet<Integer>> executedClone = new HashMap<>();
        for (String client: executed.keySet()) {
            executedClone.put(client, (HashSet<Integer>) executed.get(client).clone());
        }

        for (BankCommand cmd: cmds) {
            if (canExecute(balancesClone, executedClone, cmd.getSeq(), cmd.getSource(), cmd.getAmount() + cmd.getFee())) {
                transfer(balancesClone, cmd.getSource(), cmd.getDestination(), cmd.getAmount());
                transfer(balancesClone, cmd.getSource(), cmd.getMiner(), cmd.getFee());
                valid.add(cmd);
            } else {
                invalid.add(cmd);
            }
        }

        return new Pair<>(valid, invalid);
    }

    /*
     * Returns whether all transctions in the batch can be executed
     **/
    public boolean allTransactionsValid(CommandBatch cmds) {
        return getValidFromBatch(cmds.getCommands()).getValue().size() == 0;
    }

    public Optional<Integer> update(CommandBatch cmds) {
        if (!this.allTransactionsValid(cmds)) {
            return Optional.empty();
        }

        this.forceUpdate(cmds);
        this.ops++;

        LOGGER.log(Level.INFO, MessageFormat.format("Updated state - the op id is {0}",
                    this.ops));

        return Optional.of(this.ops);
    }

    // Returns operation id
    public Optional<Integer> update(BankCommand cmd) {
        if (!this.isValidCommand(cmd)) {
            return Optional.empty();
        }

        this.forceUpdate(cmd);
        this.ops++;

        LOGGER.log(Level.INFO, MessageFormat.format("Updated state - the op id is {0}",
                    this.ops));

        return Optional.of(this.ops);
    }

    // returns balance for given id
    public int getBalance(String id) {
        return this.balances.get(id);
    }

    // returns copy of current state
    public Map<String, Integer> getState() {
        return (HashMap<String, Integer>) this.balances.clone();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BankState{");
        sb.append("ops=").append(ops).append(", ");
        sb.append("balances={");
        balances.forEach((key, value) -> sb.append(key).append("=").append(value).append(", "));
        if (!balances.isEmpty()) {
            sb.setLength(sb.length() - 2); // remove the last ", "
        }
        sb.append("}, ");
        sb.append("history=[");
        for (BankCommand cmd : history) {
            sb.append(cmd.toString()).append(", ");
        }
        if (!history.isEmpty()) {
            sb.setLength(sb.length() - 2); // remove the last ", "
        }
        sb.append("], ");
        sb.append("executed={");
        executed.forEach((key, value) -> {
            sb.append(key).append("=[");
            value.forEach(seq -> sb.append(seq).append(", "));
            if (!value.isEmpty()) {
                sb.setLength(sb.length() - 2); // remove the last ", "
            }
            sb.append("], ");
        });
        if (!executed.isEmpty()) {
            sb.setLength(sb.length() - 2); // remove the last ", "
        }
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }
}
