package pt.ulisboa.tecnico.hdsledger.consensus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
	// TODO (dsa): also exists in Instanbul class, factor out to some utils type thing
	private ConsensusMessage createPrepareMessage(int id, String value, int instance, int round, int receiver) {
		PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

		return new ConsensusMessageBuilder(id, Type.PREPARE)
			.setConsensusInstance(instance)
			.setRound(round)
			.setMessage(prePrepareMessage.toJson())
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
}
