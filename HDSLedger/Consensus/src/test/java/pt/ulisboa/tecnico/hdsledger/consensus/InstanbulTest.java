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

	/**
	 * Runs an instance of consensus where all nodes input the same value
	 */
	@Test
	public void simpleConsensus() {
		int n = 4;
		int lambda = 0;
		String value = "a";

		List<ProcessConfig> configs = defaultConfigs(n);
		List<Instanbul> instances = configs.stream()
			.map(config -> new Instanbul(config, lambda))
			.collect(Collectors.toList());

		// Backlog of messages
		Deque<ConsensusMessage> messages = new LinkedList();

		instances.forEach(instance -> {
			// Start every replica
			List<ConsensusMessage> output = instance.start(value);

			// Add all messages to be processed
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
	}
}
