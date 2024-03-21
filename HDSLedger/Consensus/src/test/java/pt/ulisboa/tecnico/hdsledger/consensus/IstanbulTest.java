package pt.ulisboa.tecnico.hdsledger.consensus;

import com.google.gson.Gson;
import javafx.util.Pair;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.MessageCreator;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IstanbulTest {



	// n is set to 10 by default
	@BeforeAll
    public static void genKeys() throws GeneralSecurityException, IOException {
		int n = 12;
		List<String> publicKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());

		List<String> privateKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/priv_%d.key", i))
			.collect(Collectors.toList());

		for (int i = 0 ; i < n; i++) {
			try {
				RSAKeyGenerator.read(privateKeys.get(i), "priv");
				RSAKeyGenerator.read(publicKeys.get(i), "pub");
			} catch (GeneralSecurityException | IOException e) {
				RSAKeyGenerator.write(privateKeys.get(i), publicKeys.get(i));
			}
		}
	}

	private List<ProcessConfig> defaultConfigs(int n) {
		List<String> publicKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());

		List<String> privateKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/priv_%d.key", i))
			.collect(Collectors.toList());

		return IntStream.range(0, n).mapToObj(i ->
			new ProcessConfig(
				"localhost",
				i,
				20000 + i,
				30000 + i,
				n,
				publicKeys.get(i),
				privateKeys.get(i)
			)
		).collect(Collectors.toList());
	}

	/*
	 * Create set of instances that doesn't use external predicate.
	 */
	private List<Istanbul> defaultInstances(int n, Map<Integer, List<String>> confirmed, int lambda, Deque<ConsensusMessage> messages) {
		return defaultInstancesWithPredicate(n, confirmed, lambda, messages, value -> true);
	}

	private List<Istanbul> defaultInstancesWithPredicate(int n, Map<Integer, List<String>> confirmed, int lambda, Deque<ConsensusMessage> messages, Predicate<String> beta) {
		List<ProcessConfig> configs = defaultConfigs(n);
		
		System.out.printf("pk 0: %s\n", configs.get(0).getPublicKey());

		List<Istanbul> instances = configs.stream()
			.map(config -> {
				// Create instance
				Istanbul i = new Istanbul(configs, config, lambda, beta);

				// Register callback for deliver
				int id = config.getId();
				List<String> delivered = new ArrayList<>();
				confirmed.put(id, delivered);
				i.registerObserver(s -> delivered.add(s));

				return i;
			}).collect(Collectors.toList());

		for (int i = 0; i < n; i++) {
			Istanbul instance = instances.get(i);

			// Create a callback that handles timeout and stores messages
			Consumer<Integer> callback = timerId -> {
				List<ConsensusMessage> output = instance.handleTimeout(timerId);
				for (ConsensusMessage m: output) {
					messages.addLast(m);	
				}
			};

			// Create timer with that callback
			Timer timer = new SimpleTimer();
			timer.registeTimeoutCallback(callback);

			// Register timer as the one to be used by instance
			instance.setTimer(timer);
		}
		return instances;
	}
	/**
	 * Checks that every list in the map provided has a unique string, which
	 * is the same across all lists
	 *
	 * @param confirmed map of lists of confirmed value
	 * @return the value everyone agreed to
	 */
	private String checkConfirmed(Map<Integer, List<String>> confirmed) {
		Set<String> outputs = new HashSet();
		for (Map.Entry<Integer, List<String>> entry: confirmed.entrySet()) {
			List<String> delivered = entry.getValue();
			System.out.printf("[test] Delivered by %d: %s\n",
					entry.getKey(),
					String.join(", ", delivered));

			if (delivered.size() != 1) {
				throw new RuntimeException("A replica didn't deliver once (0 or multiple times)");
			}

			outputs.add(delivered.get(0));
		}

		if (outputs.size() != 1) {
			throw new RuntimeException("Different values were delivered");
		}

		return outputs.iterator().next();
	}

	private boolean checkNoOneConfirmed(Map<Integer, List<String>> confirmed) {
		for (Map.Entry<Integer, List<String>> entry: confirmed.entrySet()) {
			List<String> delivered = entry.getValue();
			System.out.printf("[test] Delivered by %d: %s\n",
					entry.getKey(),
					String.join(", ", delivered));

			if (delivered.size() != 0) {
				throw new RuntimeException("A replica delivered onceor multiple times");
			}
		}
		return true;
	}

	/**
	 * Runs an instance of consensus with 4 nodes where all nodes input the same value
	 */
	@Test
	public void simpleConsensusN4() {
		int n = 4;
		int lambda = 0;
		String value = "a";

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			List<ConsensusMessage> output = instance.start(value);

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Process all messages without any incidents
		while (messages.size() > 0) {
			ConsensusMessage message = messages.pollFirst();	
	 		if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();
			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			output.forEach(m -> messages.addLast(m));
		}

		// Check that everyone delivered the same and once only
		if (!checkConfirmed(confirmed).equals(value)) {
			throw new RuntimeException("ERROR: agreed to wrong value");
		}	
	}

	/**
	 * Runs an instance of consensus with 4 nodes where all, but one agree on
	 * value
	 */
	@Test
	public void oneDisagreeN4() {
		// TODO: run this with different lambdas
		int n = 4;
		int lambda = 0;
		String value = "a";
		String other = "b";

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			List<ConsensusMessage> output;
			if (instance.getId() == 0) {
				output = instance.start(other);
			} else {
				output = instance.start(value);
			}

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Process all messages without any incidents
		while (messages.size() > 0) {
			ConsensusMessage message = messages.pollFirst();	
	 		if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();
			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			output.forEach(m -> messages.addLast(m));
		}

		// Check that everyone delivered the same and once only
		String decidedValue = checkConfirmed(confirmed);
		if (!decidedValue.equals(value) && !decidedValue.equals(other)) {
			throw new RuntimeException("ERROR: agreed to wrong value");
		}	
	}

	/**
	 * Runs an instance of consensus with 4 nodes where the decision if 50/50
	 */
	@Test
	public void halfDisagreeN4() {
		// TODO: run this with different lambdas
		int n = 4;
		int lambda = 0;
		String value = "a";
		String other = "b";

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			List<ConsensusMessage> output;
			if (instance.getId() < n/2) {
				output = instance.start(other);
			} else {
				output = instance.start(value);
			}

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Process all messages without any incidents
		while (messages.size() > 0) {
			ConsensusMessage message = messages.pollFirst();	
	 		if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();
			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			output.forEach(m -> messages.addLast(m));
		}

		// Check that everyone delivered the same and once only
		String decidedValue = checkConfirmed(confirmed);
		if (!decidedValue.equals(value) && !decidedValue.equals(other)) {
			throw new RuntimeException("ERROR: agreed to wrong value");
		}	
	}

	/**
	 * Runs an instance of consensus with 4 nodes where everyone proposes
	 * a different value
	 */
	@Test
	public void allDisagreeN4() {
		// TODO: run this with different lambdas
		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			String value = String.format("a%d", instance.getId());
			List<ConsensusMessage> output = instance.start(value);

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Process all messages without any incidents
		while (messages.size() > 0) {
			ConsensusMessage message = messages.pollFirst();	
	 		if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();
			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			output.forEach(m -> messages.addLast(m));
		}

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	/**
	 * Runs an instance of consensus with 4 nodes where one node that is not
	 * the first leader crashes
	 */
	@Test
	public void nonLeaderCrashesN4() {
		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			String value = String.format("a%d", instance.getId());
			List<ConsensusMessage> output = instance.start(value);

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Last node crashes
		while (messages.size() > 0) {
			ConsensusMessage message = messages.pollFirst();	
	 		if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();
			if (receiver == n-1) continue;
			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			output.forEach(m -> messages.addLast(m));
		}

		// Crashed node is node expected to output anything
		confirmed.remove(n-1);

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	/**
	 * Runs an instance of consensus with 4 nodes where one node that is
	 * the first leader crashes
	 */
	@Test
	public void leaderCrashesN4() {
		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			if (instance.getId() != 0) {
				String value = String.format("a%d", instance.getId());
				List<ConsensusMessage> output = instance.start(value);
				// Store all messages to be processed
				output.forEach(m -> messages.addLast(m));
			}
		});

		// Run for at most 2 seconds
		long startTime = System.currentTimeMillis();
        long duration = 0;
		while (duration < 5000) {
			while (messages.size() > 0) {
				ConsensusMessage message = messages.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				// FIXME (dsa): don't hard code the leader
				if (receiver == 0) continue;
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages.addLast(m));
			}

			duration = System.currentTimeMillis() - startTime;
		}

		// Crashed node is not node expected to output anything
		confirmed.remove(0);

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	/**
	 * Runs an instance of consensus with 4 nodes where one node that is
	 * the first leader crashes and later comes back to life (as if network
	 * partitioned)
	 */
	@Test
	public void leaderCrashesAndComesBackN4() {
		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Backlog of messages (after partition)
		Deque<ConsensusMessage> messages2 = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			if (instance.getId() != 0) {
				String value = String.format("a%d", instance.getId());
				List<ConsensusMessage> output = instance.start(value);
				// Store all messages to be processed
				output.forEach(m -> messages.addLast(m));
			}
		});

		// Run for at most 2 seconds
		long startTime = System.currentTimeMillis();
        long duration = 0;
		while (duration < 5000) {
			while (messages.size() > 0) {
				ConsensusMessage message = messages.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				// FIXME (dsa): don't hard code the leader
				if (receiver == 0) {
					messages2.addLast(message);
					continue;
				};
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages.addLast(m));
			}

			duration = System.currentTimeMillis() - startTime;
		}

		while (messages2.size() > 0) {
				ConsensusMessage message = messages2.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages2.addLast(m));
		}

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	/**
	 * Runs an instance of consensus with 4 nodes where network partitions
	 * after all prepare
	 */
	@Test
	public void commitsNotGoingThroughInFirstRoundN4() {
		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Backlog of messages (after partition)
		Deque<ConsensusMessage> messages2 = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			String value = String.format("a%d", instance.getId());
			List<ConsensusMessage> output = instance.start(value);
			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Run for at most 5 seconds
		long startTime = System.currentTimeMillis();
        long duration = 0;
		while (duration < 5000) {
			while (messages.size() > 0) {
				ConsensusMessage message = messages.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				
				// Don't allow COMMITs to go through
				if (message.getType() == Message.Type.COMMIT) {
					messages2.addLast(message);
					continue;
				};
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages.addLast(m));
			}

			duration = System.currentTimeMillis() - startTime;
		}

		while (messages2.size() > 0) {
				ConsensusMessage message = messages2.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages2.addLast(m));
		}

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	/**
	 * Runs an instance of consensus with 4 nodes where network partitions
	 * for some seconds
	 */
	@Test
	public void networkPartitionN4() {
		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Backlog of messages (after partition)
		Deque<ConsensusMessage> messages2 = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			String value = String.format("a%d", instance.getId());
			List<ConsensusMessage> output = instance.start(value);
			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Run for at most 5 seconds
		long startTime = System.currentTimeMillis();
        long duration = 0;
		while (duration < 5000) {
			while (messages.size() > 0) {
				ConsensusMessage message = messages.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				// Don't allow messages to go through
				messages2.addLast(message);
			}

			duration = System.currentTimeMillis() - startTime;
		}

		while (messages2.size() > 0) {
				ConsensusMessage message = messages2.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages2.addLast(m));
		}

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	/**
	 * Runs an instance of consensus with 4 nodes where messages are invalid
	 */
	@Test
	public void badMessageN4() {
		int n = 4;
		int lambda = 0;
		String value = "a";

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstancesWithPredicate(n, confirmed, lambda, messages, s -> false);

		// Start every replica
		instances.forEach(instance -> {
			List<ConsensusMessage> output = instance.start(value);

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Run for at most 5 seconds
		long startTime = System.currentTimeMillis();
        long duration = 0;
		while (duration < 5000) {
			while (messages.size() > 0) {
				ConsensusMessage message = messages.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages.addLast(m));
			}

			duration = System.currentTimeMillis() - startTime;
		}

		// Check that no one delivered anything
		for (int i = 0; i < n; i++) {
			assertEquals(confirmed.get(i).size(), 0);
		}
	}

	/**
	 * Runs an instance of consensus with 4 nodes where one node that is
	 * the commit don't go through and round changes are stripped temporarily of justifications
	 */
	@Test
	public void roundChangesWithoutJustificationsN4() {
		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Backlog of messages (after partition)
		Deque<ConsensusMessage> messages2 = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			if (instance.getId() != 0) {
				String value = String.format("a%d", instance.getId());
				List<ConsensusMessage> output = instance.start(value);
				// Store all messages to be processed
				output.forEach(m -> messages.addLast(m));
			}
		});

		// Run for at most 2 seconds
		long startTime = System.currentTimeMillis();
        long duration = 0;
		while (duration < 7000) {
			while (messages.size() > 0) {
				ConsensusMessage message = messages.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();

				if (message.getType() == Message.Type.COMMIT) {
					messages2.addLast(message);
					continue;
				};

				if (message.getType() == Message.Type.ROUND_CHANGE) {
					System.out.println("Tampering with ROUND-CHANGE justification");
					RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
					roundChangeMessage.clearJustification();
					message.setMessage(roundChangeMessage.toJson());
				}

				Istanbul instance = instances.get(receiver);
				if (instance == null) System.out.println("[test] instance is null");
				if (message == null) System.out.println("[test] message is null");
				List<ConsensusMessage> output = instance.handleMessage(message);
				output.forEach(m -> messages.addLast(m));
			}

			duration = System.currentTimeMillis() - startTime;
		}

		// nothing is delivered
		Set<String> outputs = new HashSet();
		for (Map.Entry<Integer, List<String>> entry: confirmed.entrySet()) {
			List<String> delivered = entry.getValue();
			System.out.printf("[test] Delivered by %d: %s\n",
					entry.getKey(),
					String.join(", ", delivered));

			if (delivered.size() != 0) {
				throw new RuntimeException("A replica didn't deliver once (0 or multiple times)");
			}
		}

		while (messages2.size() > 0) {
				ConsensusMessage message = messages2.pollFirst();	
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();
				List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
				output.forEach(m -> messages2.addLast(m));
		}

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	/**
	 * Tests highest prepared where no one prepared yet (so there are no justifications)
	 */
	@Test
	public void highestPreparedTestAllEmpty() {
		int n = 4;
		int lambda = 0;
		int nextRound = 1;
		int me = 0;
		List<ConsensusMessage> Qrc = IntStream.range(0, n)
			.mapToObj(i -> MessageCreator.createRoundChangeMessage(me, 0, nextRound, i, Optional.empty(), Optional.empty(), Optional.empty()))
			.collect(Collectors.toList());

		Optional<Pair<String, Integer>> optPair = Istanbul.highestPrepared(Qrc); 
		assert(optPair.isEmpty());
	}

	/**
	 * Tests highest prepared where all prepared to same value and provide the
	 * same justification
	 */
	@Test
	public void highestPreparedTestAllAgreeOnValue() {
		int n = 4;
		int lambda = 0;
		int nextRound = 1;
		int me = 0;
		String value = "a";
		int round = 3;

		List<ConsensusMessage> prepares = IntStream.range(0, n)
			.mapToObj(i -> MessageCreator.createPrepareMessage(me, value, lambda, round, i))
			.collect(Collectors.toList());

		List<ConsensusMessage> Qrc = IntStream.range(0, n)
			.mapToObj(i -> MessageCreator.createRoundChangeMessage(me, 0, nextRound, i, Optional.of(value), Optional.of(round), Optional.of(prepares)))
			.collect(Collectors.toList());

		Optional<Pair<String, Integer>> optPair = Istanbul.highestPrepared(Qrc); 
		assert(optPair.isPresent());
		assertEquals(optPair.get().getKey(), value);
		assertEquals(optPair.get().getValue(), round);
	}

	/**
	 * Tests highest prepared where all prepared to different values and provide a
	 * justification for that
	 */
	@Test
	public void highestPreparedTestMultiplePrepareRounds() {
		int n = 4;
		int lambda = 0;
		int nextRound = n+10;
		int me = 0;
		String value = "a";
		int round = 3;

		List<ConsensusMessage> prepares = IntStream.range(0, n)
			.mapToObj(i -> MessageCreator.createPrepareMessage(me, String.format("a%d", i), lambda, i, i))
			.collect(Collectors.toList());

		List<ConsensusMessage> Qrc = IntStream.range(0, n)
			.mapToObj(i -> {
				int pr = i;
				String pv = String.format("value_%d", i);

				// Get justification for prepared value for round i
				List<ConsensusMessage> justification = IntStream.range(0, n)
					.mapToObj(j -> MessageCreator.createPrepareMessage(me, value, lambda, i, j))
					.collect(Collectors.toList());

				return MessageCreator.createRoundChangeMessage(me, 0, nextRound, i, Optional.of(pv), Optional.of(pr), Optional.of(justification));
				})
			.collect(Collectors.toList());

		Optional<Pair<String, Integer>> optPair = Istanbul.highestPrepared(Qrc); 
		assert(optPair.isPresent());
		assertEquals(optPair.get().getKey(), String.format("value_%d", n-1));
		assertEquals(optPair.get().getValue(), n-1);
	}

	@Test
	public void sendCommitQuorumIfRoundChangeReceivedButAlreadyDecided() {
		// objective: if a replica received a round change message for an instance that is already decided, it should
		// send the commit quorum messages that lead to the decision

		int n = 4;
		int lambda = 0;

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Backlog of messages (after partition)
		Deque<ConsensusMessage> messages2 = new ConcurrentLinkedDeque();

		// Backlog of messages late replica
		Deque<ConsensusMessage> messages3 = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);

		// Start every replica
		instances.forEach(instance -> {
			if (instance.getId() != 0) {
				String value = String.format("a%d", instance.getId());
				List<ConsensusMessage> output = instance.start(value);
				// Store all messages to be processed
				output.forEach(m -> messages.addLast(m));
			}
		});

		// Run for at most 2 seconds
		long startTime = System.currentTimeMillis();
		long duration = 0;
		while (duration < 7000) {
			while (messages.size() > 0) {
				ConsensusMessage message = messages.pollFirst();
				System.out.printf("[test] message: %s\n", new Gson().toJson(message));
				if (message == null) {
					throw new RuntimeException("ERROR: null message found");
				}

				int receiver = message.getReceiver();

				if (message.getType() == Message.Type.COMMIT || (message.getType() == Message.Type.ROUND_CHANGE && message.getSenderId() == 3)) {
					messages2.addLast(message);
					continue;
				}

				Istanbul instance = instances.get(receiver);
				if (instance == null) System.out.println("[test] instance is null");
				if (message == null) System.out.println("[test] message is null");
				List<ConsensusMessage> output = instance.handleMessage(message);
				output.forEach(m -> messages.addLast(m));
			}

			duration = System.currentTimeMillis() - startTime;
		}

		// nothing is delivered
		Set<String> outputs = new HashSet();
		for (Map.Entry<Integer, List<String>> entry: confirmed.entrySet()) {
			List<String> delivered = entry.getValue();
			System.out.printf("[test] Delivered by %d: %s\n",
					entry.getKey(),
					String.join(", ", delivered));

			if (delivered.size() != 0) {
				throw new RuntimeException("A replica didn't deliver once (0 or multiple times)");
			}
		}

		messages2.forEach(m -> {
			int receiver = m.getReceiver();
			if (receiver == 3) {
				messages2.remove(m);
			}
		});  // make sure that replica 3 doesn't have COMMIT messages to process -> forcing it to be late

		while (messages2.size() > 0) {
			ConsensusMessage message = messages2.pollFirst();
			if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();

			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			if (message.getType() == Message.Type.ROUND_CHANGE) {
				output.forEach(m -> messages3.addLast(m));
			} else {
				output.forEach(m -> messages2.addLast(m));
			}
		}

		// check that only 3 hasn't decided yet
		for (int i = 0; i < n; i++) {
			if (i == 3) {
				assertEquals(confirmed.get(i).size(), 0);
			} else {
				assertEquals(confirmed.get(i).size(), 1);
			}
		}

		// now replica 3 is going to process the received messages
		while (messages3.size() > 0) {
			ConsensusMessage message = messages3.pollFirst();
			if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			List<ConsensusMessage> output = instances.get(3).handleMessage(message);
			output.forEach(m -> messages3.addLast(m));
		}

		// Check that everyone delivered the same and once only
		checkConfirmed(confirmed); // ignore output value for simplicity
	}

	@Test
	public void badHandlerTest() {
		int n = 4;
		int lambda = 0;
		String value = "a";

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);
		// make one instances byzantine
		instances.get(0).setMessageHandler(m -> instances.get(0).badHandleMessage(m));

		// Start every replica
		instances.forEach(instance -> {
			List<ConsensusMessage> output = instance.start(value);

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Process all messages without any incidents
		while (messages.size() > 0) {
			ConsensusMessage message = messages.pollFirst();
			if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();
			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			output.forEach(m -> messages.addLast(m));
		}

		// Check that no everyone delivered the same and once only
		if (!checkConfirmed(confirmed).equals(value)) {
			throw new RuntimeException("ERROR: agreed to wrong value");
		}
	}

	@Test
	public void twoBadHandlerTest() {
		int n = 4;
		int lambda = 0;
		String value = "a";

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Backlog of messages
		Deque<ConsensusMessage> messages = new ConcurrentLinkedDeque();

		// Consensus instances
		List<Istanbul> instances = defaultInstances(n, confirmed, lambda, messages);
		// make two instances byzantine
		instances.get(0).setMessageHandler(m -> instances.get(0).badHandleMessage(m));
		instances.get(1).setMessageHandler(m -> instances.get(1).badHandleMessage(m));

		// Start every replica
		instances.forEach(instance -> {
			List<ConsensusMessage> output = instance.start(value);

			// Store all messages to be processed
			output.forEach(m -> messages.addLast(m));
		});

		// Process all messages without any incidents
		while (messages.size() > 0) {
			ConsensusMessage message = messages.pollFirst();
			if (message == null) {
				throw new RuntimeException("ERROR: null message found");
			}

			int receiver = message.getReceiver();
			List<ConsensusMessage> output = instances.get(receiver).handleMessage(message);
			output.forEach(m -> messages.addLast(m));
		}

		// Check that no everyone delivered the same and once only
		if (!checkNoOneConfirmed(confirmed)) {
			throw new RuntimeException("ERROR: agreed to wrong value");
		}
	}
}
