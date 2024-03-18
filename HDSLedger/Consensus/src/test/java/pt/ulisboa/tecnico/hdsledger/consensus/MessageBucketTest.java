package pt.ulisboa.tecnico.hdsledger.consensus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import pt.ulisboa.tecnico.hdsledger.consensus.message.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message.Type;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucketTest {
	// TODO (dsa): also exists in Istanbul class, factor out to some utils type thing
	private ConsensusMessage createPrepareMessage(int id, String value, int instance, int round, int receiver) {
		PrepareMessage prepareMessage = new PrepareMessage(value);

		return new ConsensusMessageBuilder(id, Type.PREPARE)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(prepareMessage.toJson())
			.setReceiver(receiver)
			.build();
	}

	private ConsensusMessage createCommitMessage(int id, String value, int instance, int round, int receiver) {
		CommitMessage commitMessage = new CommitMessage(value);

		return new ConsensusMessageBuilder(id, Type.COMMIT)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(commitMessage.toJson())
			.setReceiver(receiver)
			.build();
	}

	private ConsensusMessage createRoundChangeMessage(int id, int instance, int round, int receiver, Optional<String> pvi, Optional<Integer> pri, Optional<List<ConsensusMessage>> justification) {
		RoundChangeMessage roundChangeMessage = new RoundChangeMessage(pvi, pri, justification);

		return new ConsensusMessageBuilder(id, Type.ROUND_CHANGE)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(roundChangeMessage.toJson())
			.setReceiver(receiver)
			.build();
	}

	// TODO (dsa): move this to parameterized test

	/**
	 * Checks if there's a quorum when everyone sent message
	 */
	@Test
	public void allPrepareBucketTest() {
		int n = 4;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, n)
					.mapToObj(i ->
						createPrepareMessage(i, value, instance, round, receiver))
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidPrepareQuorum(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), value);
	}

	/**
	 * Checks if there's a quorum when only exactly a quorum sent messages
	 */
	@Test
	public void quorumPrepareBucketTest() {
		int n = 4;
        int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, quorumSize)
					.mapToObj(i ->
						createPrepareMessage(i, value, instance, round, receiver))
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidPrepareQuorum(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), value);
	}

	/**
	 * Checks if there's a quorum when only exactly a quorum-1 has messages there
	 */
	@Test
	public void almostQuorumPrepareBucketTest() {
		int n = 4;
        int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, quorumSize-1)
					.mapToObj(i ->
						createPrepareMessage(i, value, instance, round, receiver))
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidPrepareQuorum(round);	
		assert(!optValue.isPresent());
	}

	/**
	 * Checks if there's a quorum when no message was sent
	 */
	@Test
	public void silencePrepareBucketTest() {
		int n = 4;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		Optional<String> optValue = bucket.hasValidPrepareQuorum(round);	
		assert(!optValue.isPresent());
	}

	/**
	 * Checks if there's a quorum when only exactly a quorum sent message 'a' and
	 * the remaining sent 'b'
	 */
	@Test
	public void disagreementQuorumPrepareBucketTest() {
		int n = 4;
        int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		String good = "a";
		String bad = "b";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, n)
					.mapToObj(i -> {
						String value = i < quorumSize ? good : bad;
						return createPrepareMessage(i, value, instance, round, receiver);
					})
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidPrepareQuorum(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), good);
	}

	/**
	 * Checks if there's a quorum when everyone sent message
	 */
	@Test
	public void allCommitBucketTest() {
		int n = 4;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, n)
					.mapToObj(i ->
						createCommitMessage(i, value, instance, round, receiver))
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidCommitQuorum(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), value);
	}

	/**
	 * Checks if there's a quorum when only exactly a quorum sent messages
	 */
	@Test
	public void quorumCommitBucketTest() {
		int n = 4;
        int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, quorumSize)
					.mapToObj(i ->
						createCommitMessage(i, value, instance, round, receiver))
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidCommitQuorum(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), value);
	}

	/**
	 * Checks if there's a quorum when only exactly a quorum-1 has messages there
	 */
	@Test
	public void almostQuorumCommitBucketTest() {
		int n = 4;
        int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, quorumSize-1)
					.mapToObj(i ->
						createCommitMessage(i, value, instance, round, receiver))
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidCommitQuorum(round);	
		assert(!optValue.isPresent());
	}

	/**
	 * Checks if there's a quorum when no message was sent
	 */
	@Test
	public void silenceCommitBucketTest() {
		int n = 4;
		String value = "a";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		Optional<String> optValue = bucket.hasValidCommitQuorum(round);	
		assert(!optValue.isPresent());
	}

	/**
	 * Checks if there's a quorum when only exactly a quorum sent message 'a' and
	 * the remaining sent 'b'
	 */
	@Test
	public void disagreementQuorumCommitBucketTest() {
		int n = 4;
        int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		String good = "a";
		String bad = "b";
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, n)
					.mapToObj(i -> {
						String value = i < quorumSize ? good : bad;
						return createCommitMessage(i, value, instance, round, receiver);
					})
					.forEach(m -> bucket.addMessage(m));
	
		Optional<String> optValue = bucket.hasValidCommitQuorum(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), good);
	}

	/**
	 * Test weak support for round change message when all agree on next round (which
	 * is round+1)
	 */
	@Test
	public void allAgreeRoundChangeWeakSupportBucketTest() {
		int n = 4;
		int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		Optional<Integer> pri = Optional.empty();
		Optional<String> pvi = Optional.empty();
		Optional<List<ConsensusMessage>> justification = Optional.empty();
		int instance = 0;
		int round = 0;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, n)
			.mapToObj(i -> createRoundChangeMessage(i, instance, round+1, receiver, pvi, pri, justification))
			.forEach(m -> bucket.addMessage(m));

		Optional<Integer> optValue = bucket.hasValidWeakRoundChangeSupport(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), round+1);
	}

	/**
	 * Test weak support for round change message when all are trying to change to previous or
	 * current round
	 */
	@Test
	public void allStaleRoundChangeWeakSupportBucketTest() {
		int n = 4;
		int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		Optional<Integer> pri = Optional.empty();
		Optional<String> pvi = Optional.empty();
		Optional<List<ConsensusMessage>> justification = Optional.empty();
		int instance = 0;
		int round = 5;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, n)
			.mapToObj(i -> createRoundChangeMessage(i, instance, round, receiver, pvi, pri, justification))
			.forEach(m -> bucket.addMessage(m));

		Optional<Integer> optValue = bucket.hasValidWeakRoundChangeSupport(round);	
		assert(!optValue.isPresent());
	}

	/**
	 * Test weak support for round change message when all are trying to change
	 * to future but different rounds
	 */
	@Test
	public void allDisagreeNoStaleRoundChangeWeakSupportBucketTest() {
		int n = 4;
		int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		Optional<Integer> pri = Optional.empty();
		Optional<String> pvi = Optional.empty();
		Optional<List<ConsensusMessage>> justification = Optional.empty();
		int instance = 0;
		int round = 5;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, n)
			.mapToObj(i -> createRoundChangeMessage(i, instance, round+1+i, receiver, pvi, pri, justification))
			.forEach(m -> bucket.addMessage(m));

		Optional<Integer> optValue = bucket.hasValidWeakRoundChangeSupport(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), round+1); // should be the minimum that was seen
	}

	/**
	 * Test weak support for round change message when all are trying to change
	 * but only f+1 are in the future
	 */
	@Test
	public void allDisagreeSomeStaleRoundChangeWeakSupportBucketTest() {
		int n = 4;
		int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		Optional<Integer> pri = Optional.empty();
		Optional<String> pvi = Optional.empty();
		Optional<List<ConsensusMessage>> justification = Optional.empty();
		int instance = 0;
		int round = n+10;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);

		// 2f request are stale
		IntStream.range(0, n)
			.mapToObj(i -> createRoundChangeMessage(i, instance, round-2*f+(i+1), receiver, pvi, pri, justification))
			.forEach(m -> bucket.addMessage(m));

		Optional<Integer> optValue = bucket.hasValidWeakRoundChangeSupport(round);	
		assert(optValue.isPresent());
		assertEquals(optValue.get(), round+1); // should be the minimum that was seen
	}

	/**
	 * Test weak support for round change message when all are trying to change
	 * but only f are in the future
	 */
	@Test
	public void allDisagreeTooManyStaleRoundChangeWeakSupportBucketTest() {
		int n = 4;
		int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		Optional<Integer> pri = Optional.empty();
		Optional<String> pvi = Optional.empty();
		Optional<List<ConsensusMessage>> justification = Optional.empty();
		int instance = 0;
		int round = n+10;
		int receiver = 0; // irrelevant
		MessageBucket bucket = new MessageBucket(n);

		// 2f+1 request are stale
		IntStream.range(0, n)
			.mapToObj(i -> createRoundChangeMessage(i, instance, round-2*f+i, receiver, pvi, pri, justification))
			.forEach(m -> bucket.addMessage(m));

		Optional<Integer> optValue = bucket.hasValidWeakRoundChangeSupport(round);	
		assert(!optValue.isPresent());
	}

	/**
	 * Checks if there's a quorum when only exactly a quorum sent messages
	 */
	@Test
	public void quorumRoundChangeBucketTest() {
		int n = 4;
		int f = Math.floorDiv(n - 1, 3);
		int quorumSize = Math.floorDiv(n + f, 2) + 1;
		String value = "a";
		int instance = 0;
		int round = 0;
		Optional<Integer> pri = Optional.empty();
		Optional<String> pvi = Optional.empty();
		Optional<List<ConsensusMessage>> justification = Optional.empty();
		int receiver = 0; // irrelevant
				  //
		MessageBucket bucket = new MessageBucket(n);
		IntStream.range(0, quorumSize)
					.mapToObj(i -> createRoundChangeMessage(i, instance, round, receiver, pvi, pri, justification))
					.forEach(m -> bucket.addMessage(m));
	
		Optional<List<ConsensusMessage>> optLst = bucket.hasValidRoundChangeQuorum(round);	
		assert(optLst.isPresent());
	}

	// TODO (dsa): add tests for hasValidRoundChangeQuorum
}
