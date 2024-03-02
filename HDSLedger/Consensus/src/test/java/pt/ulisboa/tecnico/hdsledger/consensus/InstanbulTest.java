package pt.ulisboa.tecnico.hdsledger.consensus;

import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class InstanbulTest {

	private List<ProcessConfig> defaultConfigs(int n) {
		return IntStream.range(0, n).mapToObj(i ->
			new ProcessConfig(
				false,
				"localhost",
				i,
				20000 + i,
				n
			)
		).collect(Collectors.toList());
	}

	private List<Instanbul> defaultInstances(int n, Map<Integer, List<String>> confirmed, int lambda) {

		List<ProcessConfig> configs = defaultConfigs(n);
		List<Instanbul> instances = configs.stream()
			.map(config -> {
				// Create instance
				Instanbul i = new Instanbul(config, lambda);

				// Register callback for deliver
				int id = config.getId();
				List<String> delivered = new ArrayList<>();
				confirmed.put(id, delivered);
				i.registerObserver(s -> delivered.add(s));

				return i;
			}).collect(Collectors.toList());

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

		// Consensus instances
		List<Instanbul> instances = defaultInstances(n, confirmed, lambda);

		// Backlog of messages
		Deque<ConsensusMessage> messages = new LinkedList();

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
	 * Runs an instance of consensus with 10 nodes where all nodes input the same value
	 */
	@Test
	public void simpleConsensusN10() {
		int n = 10;
		int lambda = 0;
		String value = "a";

		// Stores the values confirmed by each replica
		Map<Integer, List<String>> confirmed = new HashMap<>();

		// Consensus instances
		List<Instanbul> instances = defaultInstances(n, confirmed, lambda);

		// Backlog of messages
		Deque<ConsensusMessage> messages = new LinkedList();

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

		// Consensus instances
		List<Instanbul> instances = defaultInstances(n, confirmed, lambda);

		// Backlog of messages
		Deque<ConsensusMessage> messages = new LinkedList();

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

		// Consensus instances
		List<Instanbul> instances = defaultInstances(n, confirmed, lambda);

		// Backlog of messages
		Deque<ConsensusMessage> messages = new LinkedList();

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

		// Consensus instances
		List<Instanbul> instances = defaultInstances(n, confirmed, lambda);

		// Backlog of messages
		Deque<ConsensusMessage> messages = new LinkedList();

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
}
