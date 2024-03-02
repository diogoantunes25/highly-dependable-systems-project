package pt.ulisboa.tecnico.hdsledger.consensus;

import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

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

/**
 * Instance of Instanbul consensus protocol
 *
 * TODO (dsa): - add assumptions on atomicity
 *			   - for now it's assumed that no concurrency exists	
 */
public class Instanbul {
	private static final CustomLogger LOGGER = new CustomLogger(Instanbul.class.getName());

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

	// The value passed as input to this instance
	private Optional<String> inputValuei;

	// Consensus instance -> Round -> List of prepare messages
	private final MessageBucket prepareMessages;

	// Consensus instance -> Round -> List of commit messages
	private final MessageBucket commitMessages;

	// Store if already received pre-prepare for a given round
	private final Map<Integer, Boolean> receivedPrePrepare = new HashMap<>();

	// Consensus instance information per consensus instance
	private Optional<InstanceInfo> instanceInfo;

	public Instanbul(ProcessConfig config, int lambda) {
		this.lambda = lambda;
		this.config = config;

		this.ri = 0;
		this.pri = Optional.empty();
		this.pvi = Optional.empty();
		this.inputValuei = Optional.empty();

		this.instanceInfo = Optional.empty();
		this.prepareMessages = new MessageBucket(config.getN());
		this.commitMessages = new MessageBucket(config.getN());
	}

	/**
	 * Utility to create PrePrepareMessages
	 */
	public ConsensusMessage createPrePrepareMessage(String value, int instance, int round, int receiver) {
		PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

		ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
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
	public ConsensusMessage createPrepareMessage(PrePrepareMessage prePrepareMessage, String value, int instance, int round, int receiver, int senderId, int senderMessageId) {
		PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

		ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(prepareMessage.toJson())
			.setReplyTo(senderId)
			.setReplyToMessageId(senderMessageId)
			.setReceiver(receiver)
			.build();

		return consensusMessage;
	}

	/*
	 * Start an instance of consensus for a value
	 * Only the current leader will start a consensus instance,
	 * the remaining nodes only update values.
	 *
	 * @param inputValue Value to value agreed upon
	 */
	public List<Message> start(String inputValue) {
		if (!this.instanceInfo.isPresent()) {
			this.instanceInfo = Optional.of(new InstanceInfo(inputValue));	
		}

		// Leader broadcasts PRE-PREPARE message
		if (isLeader(this.lambda, this.ri, this.config.getId())) {
			LOGGER.log(Level.INFO,
					MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));

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
	public List<Message> prePrepare(ConsensusMessage message) {

		// TODO (dsa): horrible, refactor message structure
		
		int round = message.getRound();
		int senderId = message.getSenderId();
		int senderMessageId = message.getMessageId();

		PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

		String value = prePrepareMessage.getValue();

		LOGGER.log(Level.INFO,
			MessageFormat.format(
				"{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
				config.getId(), senderId, this.lambda, round));
		
		// Verify if pre-prepare was sent by leader
		if (!isLeader(this.lambda, round, senderId)) {
		    return new ArrayList<>();
		}

		// Set instance value
		if (!this.instanceInfo.isPresent()) {
			this.instanceInfo = Optional.of(new InstanceInfo(value));	
		}

		// Resend in case message hasn't yet reached leader
		// TODO (dsa): why not return empty list
		if (receivedPrePrepare.put(round, true) != null) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
						+ "replying again to make sure it reaches the initial sender",
						config.getId(), this.lambda, round));
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
	public List<Message> prepare(ConsensusMessage message) {


		int round = message.getRound();
		int senderId = message.getSenderId();

		PrepareMessage prepareMessage = message.deserializePrepareMessage();

		String value = prepareMessage.getValue();

		LOGGER.log(Level.INFO,
				MessageFormat.format(
					"{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
					config.getId(), senderId, this.lambda, round));

		// Doesn't add duplicate messages
		prepareMessages.addMessage(message);

		// Set instance value
		if (!this.instanceInfo.isPresent()) {
			this.instanceInfo = Optional.of(new InstanceInfo(value));	
		}

		// Within an instance of the algorithm, each upon rule is triggered at most once
		// for any round r
		// Late prepare (consensus already ended for other nodes) only reply to him (as
		// an ACK)
		if (instanceInfo.get().getPreparedRound() >= round) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
						+ "replying again to make sure it reaches the initial sender",
						config.getId(), this.lambda, round));

			// Reply back to sender with PREPARE message
			ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
				.setConsensusInstance(this.lambda)
				.setRound(round)
				.setReplyTo(senderId)
				.setReplyToMessageId(message.getMessageId())
				.setMessage(instanceInfo.get().getCommitMessage().toJson())
				.setReceiver(senderId)
				.build();

			return Collections.singletonList(m);
		}

		// Find value with valid quorum
		Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), this.lambda, round);
		if (preparedValue.isPresent() && instanceInfo.get().getPreparedRound() < round) {

			// Prepare for this instance
			instanceInfo.get().setPreparedValue(preparedValue.get());
			instanceInfo.get().setPreparedRound(round);

			// Must reply to prepare message senders
			// Only send COMMIT to the ones that sent PREPARE to
			// avoid nodes that don't know anything about the 
			// round getting COMMIT
			Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(this.lambda, round)
				.values();

			CommitMessage c = new CommitMessage(preparedValue.get());
			instanceInfo.get().setCommitMessage(c);


			List<Message> output = new ArrayList<>();
			sendersMessage.forEach(senderMessage -> {
					output.add(
						new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
							.setConsensusInstance(this.lambda)
							.setRound(round)
							.setReplyTo(senderMessage.getSenderId())
							.setReplyToMessageId(senderMessage.getMessageId())
							.setMessage(c.toJson())
							.setReceiver(senderMessage.getSenderId())
							.build());
					});

			return output;
		}

		return new ArrayList<>();
	}

	/*
	 * Handle commit messages and decide if there is a valid quorum
	 *
	 * @param message Message to be handled
	 */
	public List<Message> commit(ConsensusMessage message) {

		int round = message.getRound();

		LOGGER.log(Level.INFO,
				MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
					config.getId(), message.getSenderId(), this.lambda, round));

		commitMessages.addMessage(message);

		if (!instanceInfo.isPresent()) {
			// Should never happen because only receives commit as a response to a prepare message
			MessageFormat.format(
					"{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
					config.getId(), message.getSenderId(), this.lambda, round);

			return new ArrayList<>();
		}

		// Within an instance of the algorithm, each upon rule is triggered at most once
		// for any round r
		if (instanceInfo.get().getCommittedRound() >= round) {
			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
						config.getId(), this.lambda, round));
			return new ArrayList<>();
		}

		Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(),
				this.lambda, round);

		if (commitValue.isPresent() && instanceInfo.get().getCommittedRound() < round) {

			instanceInfo.get().setCommittedRound(round);

			String value = commitValue.get();

			decide(value);

			LOGGER.log(Level.INFO,
					MessageFormat.format(
						"{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
						config.getId(), this.lambda, round, true));
			return new ArrayList<>();
		}

		return new ArrayList<>();
	}

	// TODO (dsa): add stuff for round change

	public void decide(String value) {
		// TODO (dsa): call upwards
	}

	// TODO (dsa): factor out to schedule class (to test with different
	// schedules)
	private boolean isLeader(int lambda, int round, int id) {
		int leader = (lambda + round) % config.getN();
		return leader == id;
	}
}
