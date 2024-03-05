package pt.ulisboa.tecnico.hdsledger.consensus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;

import pt.ulisboa.tecnico.hdsledger.consensus.message.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

/**
 * Container for messages. Each replica can only have a single message stored.
 * Not thread-safe.
 * Should be used for messages of same type.
 */
public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());

    // Weak support
    private final int weakSupport;
    
    // Quorum size (i.e. strong support)
    private final int quorumSize;

    // Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, ConsensusMessage>> bucket = new HashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1; // works because 4f+1 is odd
        weakSupport = f+1;
    }

    /*
     * Add a message to the bucket.
     * Validity of messages is not checked.
     * 
     * @param message
     */
    public void addMessage(ConsensusMessage message) {
        int round = message.getRound();
        bucket.putIfAbsent(round, new HashMap<>());
        bucket.get(round).put(message.getSenderId(), message);
    }

    /**
     * Returns map with list of consensus message for round and type provided,
     * indexed by value
     */
    private Map<String, List<ConsensusMessage>> groupByValue(int round, Message.Type type) {
        // Get all messages for this round of the provided type
        List<ConsensusMessage> messages = this.bucket.getOrDefault(round, new HashMap<>())
                                                                .entrySet()
                                                                .stream()
                                                                .map(e -> e.getValue())
                                                                .filter(s -> s.getType() == type)
                                                                .collect(Collectors.toList());

        // Group by value
        HashMap<String, List<ConsensusMessage>> grouped = new HashMap<>();
        messages.forEach(message -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            String value = prepareMessage.getValue();
            List<ConsensusMessage> lst = grouped.getOrDefault(value, new ArrayList<>());
            lst.add(message);
            grouped.put(value, lst);
        });

        return grouped;
    }

    /**
     * Checks the exists a quorum of messages of provided type messages.
     * Returns the quorum of messages.
     */
    private Optional<List<ConsensusMessage>> hasValidQuorum(int round, Message.Type type) {
        Map<String, List<ConsensusMessage>> grouped = groupByValue(round, type);

        // Return the list of messages of size quorumSzie
        for (Map.Entry<String, List<ConsensusMessage>> entry: grouped.entrySet()) {
            if (entry.getValue().size() >= quorumSize) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Checks the existence of f+1 messages messages of provided type with same value
     * Returns the list that satisfies for one such value
     */
    private Optional<List<ConsensusMessage>> hasWeakSupport(int round, Message.Type type) {
        Map<String, List<ConsensusMessage>> grouped = groupByValue(round, type);

        // Return the list of messages of size quorumSzie
        for (Map.Entry<String, List<ConsensusMessage>> entry: grouped.entrySet()) {
            if (entry.getValue().size() >= weakSupport) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();

    }

    /**
     * Checks the exists a quorum of messages PREPARE messages
     */
    public Optional<String> hasValidPrepareQuorum(int round) {
        Optional<List<ConsensusMessage>> messages = this.hasValidQuorum(round, Message.Type.PREPARE);

        if (messages.isPresent()) {
            ConsensusMessage message = messages.get().get(0);
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            return Optional.of(prepareMessage.getValue());
        }

        return Optional.empty();
    }

    /**
     * Checks the exists a quorum of messages COMMIT messages
     */
    public Optional<String> hasValidCommitQuorum(int round) {
        Optional<List<ConsensusMessage>> messages = this.hasValidQuorum(round, Message.Type.COMMIT);

        if (messages.isPresent()) {
            ConsensusMessage message = messages.get().get(0);
            CommitMessage commitMessage = message.deserializeCommitMessage();
            return Optional.of(commitMessage.getValue());
        }

        return Optional.empty();
    }

    /**
     * Checks the existence of f+1 messages COMMIT messages
     * Returns one value of if it's the case or empty if no value satisfies that.
     */
    public Optional<String> hasValidWeakCommitSupport(int round) {
        Optional<List<ConsensusMessage>> messages = this.hasWeakSupport(round, Message.Type.COMMIT);

        if (messages.isPresent()) {
            ConsensusMessage message = messages.get().get(0);
            CommitMessage commitMessage = message.deserializeCommitMessage();
            return Optional.of(commitMessage.getValue());
        }
        return Optional.empty();
    }

    public Map<Integer, ConsensusMessage> getMessages(int round) {
        return bucket.get(round);
    }
}
