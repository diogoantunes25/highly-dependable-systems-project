package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.APLink;
import pt.ulisboa.tecnico.hdsledger.service.Slot;

import java.util.List;
import java.util.Deque;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.concurrent.LinkedBlockingDeque;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class NodeServiceTest {

	// FIXME (dsa): don't like this basePort here
	private List<ProcessConfig> defaultConfigs(int n, int basePort) {
		return IntStream.range(0, n).mapToObj(i ->
			new ProcessConfig(
				false,
				"localhost",
				i,
				basePort + i,
				n
			)
		).collect(Collectors.toList());
	}

	private List<Link> defaultLinks(int n, List<ProcessConfig> configs) {
		ProcessConfig[] configsArray = new ProcessConfig[n];
		configs.toArray(configsArray);	
		return configs
				.stream()
				.map(config -> 
					new APLink(config,
						config.getPort(),
						configsArray,
						ConsensusMessage.class))
				.collect(Collectors.toList());
	}

	List<NodeService> setupServices(int n, int basePort) {
		List<ProcessConfig> configs = defaultConfigs(n, basePort);
		ProcessConfig[] configsArray = new ProcessConfig[n];
		configs.toArray(configsArray);	
		List<Link> links = defaultLinks(n, configs);

		List<NodeService> services = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ProcessConfig config = configs.get(i);
			Link link = links.get(i);
			services.add(new NodeService(link, config, configsArray));
		}

		return services;
	}

	@Test
	void singleExecutionTest() {
		int n = 4;
		int basePort = 10000;
		String nonce = "123";
		String cmd = "a";
		Deque<Slot> confirmedSlots = new LinkedBlockingDeque<>();
		Consumer<Slot> observer = s -> confirmedSlots.add(s);

		List<NodeService> services = setupServices(n, basePort);
		services.forEach(service -> service.listen());
		services.forEach(service -> service.registerObserver(observer));

		services.forEach(service -> service.startConsensus(nonce, cmd));

		while (confirmedSlots.size() != n) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		services.forEach(service -> service.stopAndWait());

		for (Slot s: confirmedSlots) {
			System.out.println(s);
		}
	}

	@Test
	void consecutiveExecutionTest() {
		int n = 4;
		int basePort = 10020;
		String nonce1 = "123";
		String cmd1 = "a";
		String nonce2 = "1234";
		String cmd2 = "b";

		Deque<Slot> confirmedSlots = new LinkedBlockingDeque<>();
		Consumer<Slot> observer = s -> confirmedSlots.add(s);

		List<NodeService> services = setupServices(n, basePort);
		services.forEach(service -> service.listen());
		services.forEach(service -> service.registerObserver(observer));

		services.forEach(service -> service.startConsensus(nonce1, cmd1));
		services.forEach(service -> service.startConsensus(nonce2, cmd2));

		while (confirmedSlots.size() != 2 * n) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		services.forEach(service -> service.stopAndWait());

		for (Slot s: confirmedSlots) {
			System.out.println(s);
		}
	}

	@Test
	void consecutiveExecutionDisagreementTest() {
		int n = 4;
		int basePort = 10040;
		String nonce1 = "123";
		String cmd1 = "a";
		String nonce2 = "1234";
		String cmd2 = "b";

		Deque<Slot> confirmedSlots = new LinkedBlockingDeque<>();
		Consumer<Slot> observer = s -> confirmedSlots.add(s);

		List<NodeService> services = setupServices(n, basePort);
		services.forEach(service -> service.listen());
		services.forEach(service -> service.registerObserver(observer));

		services.forEach(service -> {
			if (service.getId() < n/2) {
				service.startConsensus(nonce1, cmd1);
				service.startConsensus(nonce2, cmd2);
			} else {
				service.startConsensus(nonce2, cmd2);
				service.startConsensus(nonce1, cmd1);
			}
		});
		services.forEach(service -> service.startConsensus(nonce2, cmd2));

		while (confirmedSlots.size() != 2 * n) {
			try {
				Thread.sleep(1000);
				System.out.printf("[test] confirmedSlots.size() = %d", confirmedSlots.size());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		services.forEach(service -> service.stopAndWait());

		for (Slot s: confirmedSlots) {
			System.out.println(s);
		}
	}

	@Test
	void lateInputTest() {
		int n = 4;
		int basePort = 10060;
		String nonce1 = "123";
		String cmd1 = "a";
		String nonce2 = "1234";
		String cmd2 = "b";

		Deque<Slot> confirmedSlots = new LinkedBlockingDeque<>();
		Consumer<Slot> observer = s -> confirmedSlots.add(s);

		List<NodeService> services = setupServices(n, basePort);
		services.forEach(service -> service.listen());
		services.forEach(service -> service.registerObserver(observer));

		services.forEach(service -> {
			if (service.getId() != 0) {
				service.startConsensus(nonce1, cmd1);
				service.startConsensus(nonce2, cmd2);
			}
		});

		// wait for all but 0 to output
		while (confirmedSlots.size() != 2 * (n-1)) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		System.out.println("[test] all but 0 got somewhere");

		services.get(0).startConsensus(nonce2, cmd2);
		services.get(0).startConsensus(nonce1, cmd1);

		// wait 0 to output as well
		while (confirmedSlots.size() != 2 * n) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		services.forEach(service -> service.stopAndWait());

		for (Slot s: confirmedSlots) {
			System.out.println(s);
		}
	}
}
