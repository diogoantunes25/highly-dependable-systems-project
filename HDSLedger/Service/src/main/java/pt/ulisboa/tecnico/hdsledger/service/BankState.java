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
    private Map<String, Set<Integer>> executed;

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

    // checks command has not yet been executed and that source has enough funds
    // dangerous to use if not immediatly followed by an update ( because the
    // state might go invalid in between) -> FIXME: make this method private
    public boolean isValidCommand(BankCommand cmd) {
        return !executed.getOrDefault(cmd.getSource(), new HashSet<>()).contains(cmd.getSeq()) &&
                balances.getOrDefault(cmd.getSource(), 0) >= cmd.getAmount();
    }

    // Assumes command is valied
    private void forceUpdate(BankCommand cmd) {
        if (balances.getOrDefault(cmd.getSource(), 0) - cmd.getAmount() < 0) {
            // TODO: replace with HDSLedgerException
            throw new RuntimeException("invalid update was attempted");
        }

        if (executed.getOrDefault(cmd.getSource(), new HashSet<>()).contains(cmd.getSeq())) {
            // TODO: replace with HDSLedgerException
            throw new RuntimeException("replay attack was attempted");
        }

        LOGGER.log(Level.INFO, MessageFormat.format("Transfering {0} from {1} to {2}",
                    cmd.getAmount(), cmd.getSource(), cmd.getDestination()));

        this.balances.put(cmd.getSource(), balances.getOrDefault(cmd.getSource(), 0) - cmd.getAmount());
        this.balances.put(cmd.getDestination(), balances.getOrDefault(cmd.getDestination(), 0) + cmd.getAmount());
        this.history.add(cmd);
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
