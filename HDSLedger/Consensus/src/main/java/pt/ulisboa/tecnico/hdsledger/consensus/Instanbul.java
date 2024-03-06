package pt.ulisboa.tecnico.hdsledger.consensus;

import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig; import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.stream.IntStream;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.util.Pair;

/**
 * Instance of Instanbul consensus protocol
 * It's thread safe (per instance) - this is ensured by having all public methods
 * be synchronized (or do simple operations on thread safe objects)
 */
public class Instanbul {
	private static final CustomLogger LOGGER = new CustomLogger(Instanbul.class.getName());

    // Weak support
    private final int weakSupport;
    
    // Quorum size (i.e. strong support)
    private final int quorumSize;

	// Milliseconds
	private static final int INITIAL_TIMEOUT = 10;

	// Process configuration (includes its id)
	private final ProcessConfig config;

	// The identifier of the consensus instance
	private final int lambda;

	// The current round
	private int ri;

	// The round at which the process has prepared
	private Optional<Integer> pri;

	// The value for which the process has prepared
	private Optional<String> pvi;

	// The set of messages that justify my prepared state
	private Optional<List<ConsensusMessage>> preparationJustification;

	// The value passed as input to this instance
	private Optional<String> inputValuei;

	// Consensus Round -> List of prepare messages
	private final MessageBucket prepareMessages;

	// Consensus Round -> List of commit messages
	private final MessageBucket commitMessages;

	// Consensus Round -> List of round change messages
	private final MessageBucket roundChangeMessages;

	// Store if already received pre-prepare for a given round
	private final Map<Integer, Boolean> receivedPrePrepare = new HashMap<>();

	// Callbacks to be called on deliver
	private final List<Consumer<String>> observers = new ArrayList();

	// Wheter start was called already
	private AtomicBoolean started = new AtomicBoolean(false);

	// FIXME (dsa): I'm not sure that this is needed
	private Optional<CommitMessage> commitMessage;

	// Decided value
	private Optional<String> decision;

	// PRE-PREPARE messages from future rounds (round -> list of messages)
	private Map<Integer, List<ConsensusMessage>> stashedPrePrepare = new HashMap<>();

	// PREPARE messages from future rounds (round -> list of messages)
	private Map<Integer, List<ConsensusMessage>> stashedPrepare = new HashMap<>();

	// ROUND-CHANGE messages from other rounds (round -> list of messages)
	// TODO (dsa): not sure I'll need this, but I feel like I do
	private Map<Integer, List<ConsensusMessage>> stashedRoundChange = new HashMap<>();

	// Timer required by IBFT
	private Optional<Timer> timer = Optional.empty();

	// Timer ids for each round
	// The semantics are:
	//	- if there's no entry for r, timer not stopped
	//	- if there's a non-empty entry for r, timer running with the id inside the optinal
	//	- if there's an empty entry for r, timer stopped
	private Map<Integer, Optional<Integer>> roundTimerId = new HashMap<>();

	// Validity verifying function
	private Predicate<String> beta;

	public Instanbul(ProcessConfig config, int lambda, Predicate<String> beta) {
		int n = config.getN();
		int f = Math.floorDiv(n - 1, 3);
        this.quorumSize = Math.floorDiv(n + f, 2) + 1; // works because 4f+1 is odd
        this.weakSupport = f+1;

		this.lambda = lambda;
		this.config = config;
		this.beta = beta;

		this.ri = 0;
		this.pri = Optional.empty();
		this.pvi = Optional.empty();
		this.preparationJustification = Optional.empty();
		this.inputValuei = Optional.empty();
		this.decision = Optional.empty();

		this.commitMessage = Optional.empty();
		this.prepareMessages = new MessageBucket(n);
		this.commitMessages = new MessageBucket(n);
		this.roundChangeMessages = new MessageBucket(n);
	}

	/**
	 * Sets timer to be used by protocol
	 */
	public void setTimer(Timer timer) {
		this.timer = Optional.of(timer);
	}

	/**
	 * Encapsulates timer schedule (in paper represented by function t)
	 */
	private int getTimeout(int round) {
		// Exponential backoff
		return (1 << round) * INITIAL_TIMEOUT;
	}

	/**
	 * Add callback to be called on deliver
	 *
	 * @param observer A function that takes a single argument and returns void
	 * to be called when the instance delivers
	 */
	public void registerObserver(Consumer<String> observer) {
		observers.add(observer);	
	}

	/**
	 * Start timer with provided timeout for current round
	 * If timer was already stopped, it's not started again
	 */
	private void startTimer(int timeout) {
		// If I have a timer
		if (this.timer.isPresent()) {

			// TODO (dsa): need to make sure I haven't stopped the timer for round i
			// If I haven't started a timer for this round
			if (!roundTimerId.containsKey(this.ri)) {
				int timerId = this.timer.get().setTimerToRunning(timeout);
				roundTimerId.put(this.ri, Optional.of(timerId));
			}
		} else {
			LOGGER.log(Level.WARNING, MessageFormat.format("{0} - No timer available - liveness cannot be guaranteed",
						config.getId(), this.lambda));
		}
	}

	/**
	 * Stop timer for provided round
	 */
	private void stopTimer(int round) {
		// If I have a timer
		if (this.timer.isPresent()) {
			// If I haven't started a timer for this round
			if (!roundTimerId.containsKey(round)) {
				// nothing to stop in this case, because it was not running
			} else {
				// actually stop timer
				this.timer.get().setTimerToStopped(roundTimerId.get(round).get());
			}

			roundTimerId.put(round, Optional.empty());
		} else {
			LOGGER.log(Level.WARNING, MessageFormat.format("{0} - No timer available - liveness cannot be guaranteed",
						config.getId(), this.lambda));
		}
	}

	/**
	 * Returns round that started timer with provided timerId
	*/
	private Optional<Integer> getRoundWithTimerId(int timerId) {
		for (Map.Entry<Integer, Optional<Integer>> entry : roundTimerId.entrySet()) {
			Optional<Integer> value = entry.getValue();
			if (value.isPresent() && value.get() == timerId) {
				return Optional.of(entry.getKey());
			}
		}
		return Optional.empty(); // Value not found	
	}

	/**
	 * Utility to create PrePrepareMessages
	 */
	private ConsensusMessage createPrePrepareMessage(String value, int instance, int round, int receiver) {
		PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

		ConsensusMessage consensusMessage = new ConsensusMessageBuilder(this.config.getId(), Message.Type.PRE_PREPARE)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(prePrepareMessage.toJson())
			.setReceiver(receiver)
			.build();

		return consensusMessage;
	}

	/**
	 * Utility to create PrepareMessages
	 */
	private ConsensusMessage createPrepareMessage(PrePrepareMessage prePrepareMessage, String value, int instance, int round, int receiver, int senderId, int senderMessageId) {
		PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

		ConsensusMessage consensusMessage = new ConsensusMessageBuilder(this.config.getId(), Message.Type.PREPARE)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(prepareMessage.toJson())
			.setReplyTo(senderId)
			.setReplyToMessageId(senderMessageId)
			.setReceiver(receiver)
			.build();

		return consensusMessage;
	}

	/**
	 * Utility to create CommitMessages
	 */
	private ConsensusMessage createCommitMessage(int instance, int round, int receiver, String commitMessageJson) {
		return new ConsensusMessageBuilder(this.config.getId(), Message.Type.COMMIT)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(commitMessageJson)
			.setReceiver(receiver)
			.build();
	}

	/**
	 * Utility to create RoundChangeMessages
	 */
	private ConsensusMessage createRoundChangeMessage(int instance, int round, int receiver, Optional<String> pvi, Optional<Integer> pri, Optional<List<ConsensusMessage>> justification) {
		RoundChangeMessage roundChangeMessage = new RoundChangeMessage(pvi, pri, justification);

		return new ConsensusMessageBuilder(this.config.getId(), Message.Type.ROUND_CHANGE)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(roundChangeMessage.toJson())
			.setReceiver(receiver)
			.build();
	}

	/*
	 * Start an instance of consensus for a value
	 * Only the current leader will start a consensus instance,
	 * the remaining nodes only update values.
	 *
	 * @param inputValue Value to value agreed upon
	 */
	public synchronized List<ConsensusMessage> start(String inputValue) {
		if (started.getAndSet(true)) {
			LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
						config.getId(), this.lambda));
			return new ArrayList<>();
		}

		if (!this.inputValuei.isPresent()) {
			this.inputValuei = Optional.of(inputValue);
		}

		// Start timer for this round
		this.startTimer(getTimeout(this.ri));

		// Leader broadcasts PRE-PREPARE message
		if (isLeader(this.ri, this.config.getId())) {
			LOGGER.log(Level.INFO,
					MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));

			// Note that I should broadcast even if we're already on a later round
			// because if I was elected leader and didn't have input, that means
			// I'm still on time to try to finish of the round
			
			// Broadcast message PRE-PREPARE(lambda, ri, inputvalue)
			return IntStream.range(0, this.config.getN())
				.mapToObj(receiver -> this.createPrePrepareMessage(inputValue, this.lambda, this.ri, receiver))
				.collect(Collectors.toList());
		} else {
			LOGGER.log(Level.INFO,
					MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));

			return new ArrayList();
		}
	}

	/*
	 * Handle pre prepare messages and if the message
	 * came from leader and is justified them broadcast prepare
	 *
	 * @param message Message to be handled
	 */
	private List<ConsensusMessage> prePrepare(ConsensusMessage message) {

		// TODO (dsa): horrible, refactor message structure
		int round = message.getRound();
		int senderId = message.getSenderId();

		if (round != this.ri) {
			LOGGER.log(Level.WARNING,
					MessageFormat.format(
						"{0} - Shouldn't process PREPARE message from {1}: Consensus Instance {2}, Round {3} at round {4}",
						config.getId(), senderId, this.lambda, round, this.ri));
			throw new RuntimeException("Should not be processing PREPARE message in wrong round");
		}

		int senderMessageId = message.getMessageId();

		PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

		String value = prePrepareMessage.getValue();

		LOGGER.log(Level.INFO,
			MessageFormat.format(
				"{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
				config.getId(), senderId, this.lambda, round));
		
		// Verify if pre-prepare was sent by leader
		if (!isLeader(round, senderId)) {
		    return new ArrayList<>();
		}

		// Verify that proposed message is valid
		if (!this.beta.test(value)) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - PRE-PREPARE message from {1} Consensus Instance {2}, Round {3} is invalid (i.e. failed beta predicate)",
						config.getId(), senderId, this.lambda, round));
		    return new ArrayList<>();
		}

		// Set instance value
		// if (!this.inputValuei.isPresent()) {
		// 	this.inputValuei = Optional.of(value);
		// }

		// Resend in case message hasn't yet reached leader
		// TODO (dsa): why not return empty list
		if (receivedPrePrepare.put(round, true) != null) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
						+ "replying again to make sure it reaches the initial sender",
						config.getId(), this.lambda, round));
			return new ArrayList<>();
		}


		// Broadcast Prepare (labmda, round, value)
		return IntStream.range(0, this.config.getN())
			.mapToObj(receiver -> this.createPrepareMessage(prePrepareMessage, value, this.lambda, round, receiver, senderId, senderMessageId))
			.collect(Collectors.toList());
	}

	/*
	 * Handle prepare messages and if there is a valid quorum broadcast commit
	 *
	 * @param message Message to be handled
	 */
	private List<ConsensusMessage> prepare(ConsensusMessage message) {

		int round = message.getRound();
		int senderId = message.getSenderId();

		if (round != this.ri) {
			LOGGER.log(Level.WARNING,
					MessageFormat.format(
						"{0} - Shouldn't process PREPARE message from {1}: Consensus Instance {2}, Round {3} at round {4}",
						config.getId(), senderId, this.lambda, round, this.ri));
			throw new RuntimeException("Should not be processing PREPARE message in wrong round");
		}

		PrepareMessage prepareMessage = message.deserializePrepareMessage();

		String value = prepareMessage.getValue();

		LOGGER.log(Level.INFO,
				MessageFormat.format(
					"{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
					config.getId(), senderId, this.lambda, round));

		// Doesn't add duplicate messages
		prepareMessages.addMessage(message);

		// Set instance value
		if (!this.inputValuei.isPresent()) {
			this.inputValuei = Optional.of(value);
		}

		// If already prepared, won't prepare again
		if (this.pri.isPresent()) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Already prepared for Consensus Instance {1}, Round {2}, "
						+ "ignoring PREPARE that was just received",
						config.getId(), this.lambda, round));

			return new ArrayList<>();
		}

		// Find value with valid quorum
		Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(round);
		
		if (preparedValue.isPresent()) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Found quorum of PREPARE messages for Consensus Instance {1}, Round {2}",
						config.getId(), this.lambda, round));

			// Prepare for this instance
			this.pri = Optional.of(round);
			this.pvi = Optional.of(preparedValue.get());
			this.preparationJustification = prepareMessages.getPrepareQuorumJustification(round);

			Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(round)
				.values();
			this.commitMessage = Optional.of(new CommitMessage(preparedValue.get()));

			return IntStream.range(0, this.config.getN())
				.mapToObj(receiver -> 
					createCommitMessage(this.lambda, round, receiver, this.commitMessage.get().toJson()))
				.collect(Collectors.toList());
		}

		return new ArrayList<>();
	}

	/*
	 * Handle commit messages and decide if there is a valid quorum
	 *
	 * @param message Message to be handled
	 */
	private List<ConsensusMessage> commit(ConsensusMessage message) {

		int round = message.getRound();

		LOGGER.log(Level.INFO,
				MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
					config.getId(), message.getSenderId(), this.lambda, round));

		commitMessages.addMessage(message);

		Optional<String> commitValue = commitMessages.hasValidCommitQuorum(round);

		if (commitValue.isPresent()) {

			String value = commitValue.get();

			decide(value);

			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Decided on Consensus Instance {1}, Round {2}",
						config.getId(), this.lambda, round));

			return new ArrayList<>();
		}

		return new ArrayList<>();
	}

	/**
	 * Checks that a round change message is properly signed
	 */
	boolean prepareSignedIsValid(ConsensusMessage message) {
		// TODO (dsa): fixme
		return true;
	}

	/**
	 * Does basic verification for validity of round change message, namely
	 *	- checks that either all pri, pvi are set or non is
	 *	- if pri, pvi and justification are set, checks signatures on justification
	 */
	private boolean sanityCheckRoundChangeMessage(RoundChangeMessage message) {
		// If three fields aren't either all set or none set, bad behaviour
		if (!(message.getPvi().isPresent() && message.getPri().isPresent() && message.getJustification().isPresent())
				&& !(!message.getPvi().isPresent() && !message.getPri().isPresent() && !message.getJustification().isPresent())) {
			return false;
		}

		// If there's no prepared value, nothing to justify
		if (!message.getPvi().isPresent()) {
			return true;
		}

		return message.getJustification()
			.get()
			.stream()
			.allMatch(m -> prepareSignedIsValid(m));
	}

	/**
	 * Helper function from paper
	 * @param Qrc list of round change messages
	 */
	static Optional<Pair<String, Integer>> highestPrepared(List<ConsensusMessage> Qrc) {

		// Get all messages that have some prepared value and reverse sort by round
		Optional<ConsensusMessage> message = Qrc.stream()
			.filter(m -> m.deserializeRoundChangeMessage().getPvi().isPresent())
			.sorted((m1, m2) -> 
				Integer.compare(m2.deserializeRoundChangeMessage().getPri().get(),
								m1.deserializeRoundChangeMessage().getPri().get()))
			.findFirst();

		if (!message.isPresent()) {
			return Optional.empty();
		}

		RoundChangeMessage roundChangeMessage = message.get().deserializeRoundChangeMessage();

		return Optional.of(
				new Pair<>(roundChangeMessage.getPvi().get(),
							roundChangeMessage.getPri().get()));
	}

	/**
	 * Approximation of JUSTIFYROUNDCHANGE from paper
	 * Assumes each round change message was already verifies
	 * @param Qrc list of round change messages
	 *
	 */
	boolean justifyRoundChange(List<ConsensusMessage> Qrc) {
		if (Qrc.size() < this.quorumSize) {
			LOGGER.log(Level.INFO,
					MessageFormat.format("{0} - justification of round change was askes for a quorum with less than required size {1} < {2} (which is strange)",
						config.getId(), Qrc.size(), this.quorumSize));

			return false;
		}

		Optional<Pair<String, Integer>> optPair = highestPrepared(Qrc);
		// satisfies J1
		if (!optPair.isPresent()) {
			return true;
		}

		String pv = optPair.get().getKey();
		int pr = optPair.get().getValue();

		// Get all PREPARE values with justification for round pr with value pv
		long matches = Qrc.stream()
			.map(m -> m.deserializeRoundChangeMessage().getJustification())
			.filter(opt -> opt.isPresent())
			.flatMap(opt -> opt.get().stream())
			.filter(m -> m.getRound() == pr)
			.map(m -> m.deserializePrepareMessage().getValue())
			.filter(v -> v.equals(pv))
			.count();

		// whether J2 is satisfied
		return matches >= this.quorumSize;
	}

	/*
	 * Handle round change messages
	 *
	 * @param message Message to be handled
	 */
	private List<ConsensusMessage> roundChange(ConsensusMessage message) {
		List<ConsensusMessage> messages = new ArrayList<>();

		int round = message.getRound();
		RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

		LOGGER.log(Level.INFO,
				MessageFormat.format("{0} - Received ROUND-CHANGE message from {1}: Consensus Instance {2}, Round {3}",
					config.getId(), message.getSenderId(), this.lambda, round));

		if (!sanityCheckRoundChangeMessage(roundChangeMessage)) {
			LOGGER.log(Level.WARNING,
					MessageFormat.format("{0} - Bad ROUND-CHANGE message from {1}: Consensus Instance {2}, Round {3} - either values are inconsistent or are wrong",
						config.getId(), message.getSenderId(), this.lambda, round));

			return messages;
		}

		roundChangeMessages.addMessage(message);

		// Amplification
		Optional<Integer> optNextRound = roundChangeMessages.hasValidWeakRoundChangeSupport(this.ri);
		if (optNextRound.isPresent()) {
			// update round (ri <- rmin)
			this.ri = optNextRound.get();

			// No need to check whether I already broadcasted a ROUND-CHANGE (because
			// I just changed rounds)

			// start timer
			startTimer(getTimeout(this.ri));

			// broadcast ROUND-CHANGE(ri, pri, pvi)
			messages.addAll(IntStream.range(0, this.config.getN())
				.mapToObj(receiver -> 
					createRoundChangeMessage(this.lambda, this.ri, receiver, this.pvi, this.pri, this.preparationJustification))
				.collect(Collectors.toList()));
		}

		// no point in collecting quorum, if I'm not the leader for the next round
		if (!isLeader(this.ri, this.config.getId())) {
			return messages;
		}

		// Check existance of quorum
		Optional<List<ConsensusMessage>> optQrc = roundChangeMessages.hasValidRoundChangeQuorum(this.ri);
		if (optQrc.isPresent() && justifyRoundChange(optQrc.get())) {
			Optional<Pair<String, Integer>> optPair = highestPrepared(optQrc.get());

			// lines 12 - 16 of protocol
			Optional<String> v;
			if (optPair.isPresent()) {
				v = Optional.of(optPair.get().getKey());
			} else {
				v = inputValuei;
			}

			if (v.isPresent()) {
				// Broadcast message PRE-PREPARE(lambda, ri, inputvalue)
				messages.addAll(IntStream.range(0, this.config.getN())
					.mapToObj(receiver -> this.createPrePrepareMessage(v.get(), this.lambda, this.ri, receiver))
					.collect(Collectors.toList()));
			} else {
				// TODO (dsa): check that this is the case
				// if v is not present, that means that no input has been made
				// to this replica
				// if this is the case, it's ok for the replica not to start
				// the broadcast even though it's the leader (it will just
				// be round-changed)

				LOGGER.log(Level.WARNING,
						MessageFormat.format("{0} -  I (replica {1}) was just \"elected\", but I don't have any input: Consensus Instance {2}, Round {3} - either values are inconsistent or are wrong",
							config.getId(), message.getSenderId(), this.lambda, round));
			}
		}

		return messages;
	}

	// TODO: add stuff from algorithm 4

	/**
	 * Handles timer expiration and returns messages to be sent over the network
	 */
	public synchronized List<ConsensusMessage> handleTimeout(int timerId) {
		Optional<Integer> roundOpt = getRoundWithTimerId(timerId);

		if (!roundOpt.isPresent()) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Timeout of timer {2} on Consensus Instance {1} that either was stopped or not stated. SHOULD NOT HAPPEN",
						config.getId(), this.lambda, timerId));
			return new ArrayList<>();
		}

		int round = roundOpt.get();

		LOGGER.log(Level.INFO,
				MessageFormat.format(
					"{0} - Timeout on Consensus Instance {1}, Round {2}",
					config.getId(), this.lambda, round));

		// Broadcast RoundChange (labmda, round, pvi, pri)
		return IntStream.range(0, this.config.getN())
			.mapToObj(receiver -> this.createRoundChangeMessage(this.lambda, round, receiver, this.pvi, this.pri, this.preparationJustification))
			.collect(Collectors.toList());
	}

	/**
	 * Handles protocol messages and returns messages to be sent over the network
	 */
	public synchronized List<ConsensusMessage> handleMessage(ConsensusMessage message) {
		int mround = message.getRound();

		return switch (message.getType()) {
			case PRE_PREPARE -> {
				if (mround == this.ri) {
					yield prePrepare(message);
				} else if (message.getRound() < this.ri) {
					// PRE-PREPARES from previous rounds can be dropped
					LOGGER.log(Level.INFO,
							MessageFormat.format(
								"{0} - Ignored PRE-PREPARE at instance {1}, Round {2} (message was for {3}, which I've already moved from)",
						config.getId(), this.lambda, this.ri, mround));
					yield new ArrayList<>();
				} else {
					// PRE-PREPARE from future rounds can't processed right
					// now, but should be processed when we change rounds
					LOGGER.log(Level.INFO,
							MessageFormat.format(
								"{0} - Stashed PRE-PREPARE at instance {1}, Round {2} (message was for {3})",
						config.getId(), this.lambda, this.ri, mround));
					this.stashedPrePrepare.putIfAbsent(mround, new ArrayList<>());
					List<ConsensusMessage> stashed = stashedPrePrepare.get(mround);
					stashed.add(message);
					yield new ArrayList<>();
				}
			}

			case PREPARE -> {
				if (mround == this.ri) {
					yield prepare(message);
				} else if (message.getRound() < this.ri) {
					// PREPARES from previous rounds can be dropped
					yield new ArrayList<>();
				} else {
					// PREPARE from future rounds can't processed right
					// now, but should be processed when round is changed
					LOGGER.log(Level.INFO,
							MessageFormat.format(
								"{0} - Stashed PRE-PREPARE at instance {1}, Round {2} (message was for {3})",
						config.getId(), this.lambda, this.ri, mround));
					this.stashedPrepare.putIfAbsent(mround, new ArrayList<>());
					List<ConsensusMessage> stashed = stashedPrepare.get(mround);
					stashed.add(message);
					yield new ArrayList<>();
				}
			}

			case COMMIT -> 
				commit(message);

			case ROUND_CHANGE -> 
				roundChange(message);

			default -> {
				LOGGER.log(Level.INFO,
						MessageFormat.format("{0} - Received unknown message from {1}",
							config.getId(), message.getSenderId()));

				yield new ArrayList<>();
			}
		};
	}

	// TODO (dsa): add stuff for round change

	private void decide(String value) {
		if (this.decision.isPresent()) {

			if (!this.decision.get().equals(value)) {
				throw new RuntimeException(
					String.format(
						"QBFT decided two different values (%s and %s)",
						value,
						this.decision.get()));
			}

			return;
		}

		this.decision = Optional.of(value);

		for (Consumer obs: observers) {
			obs.accept(value);
		}
	}

	// TODO (dsa): factor out to schedule class (to test with different
	// schedules)
	private boolean isLeader(int round, int id) {
		int leader = (this.lambda + round) % config.getN();
		return leader == id;
	}

	/**
	 * Returns node id
	 */
	public int getId() {
		// Doesn't need synchronization because is read only
		return this.config.getId();
	}

	/**
	 * Returns whether input was already called
	 */
	public boolean hasStarted() {
		return started.get();
	}
}
