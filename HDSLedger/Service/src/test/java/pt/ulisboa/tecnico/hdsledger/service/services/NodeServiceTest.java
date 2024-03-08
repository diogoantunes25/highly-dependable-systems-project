package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.APLink;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;

import java.security.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class NodeServiceTest {

	// n is set to 10 by default
	@BeforeAll
	private static void genKeys() {
		int n = 10;
		List<String> publicKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());

		List<String> privateKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/priv_%d.key", i))
			.collect(Collectors.toList());

		for (int i = 0 ; i < n; i++) {
			try {
				RSAKeyGenerator.write(privateKeys.get(i), publicKeys.get(i));
			} catch (GeneralSecurityException | IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	// FIXME (dsa): don't like this basePort here
	private List<ProcessConfig> defaultConfigs(int n, int basePort) {
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
				basePort + i,
				basePort + 1000 + i, // FIXME (dsa): do this properly
				n,
				publicKeys.get(i),
				privateKeys.get(i)
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

	private List<String> defaultClientKeys(int n, int nClients) {
		return IntStream.range(n, n+nClients)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());
	}

	List<NodeService> setupServices(int n, int basePort, int nClients) {
		List<ProcessConfig> configs = defaultConfigs(n, basePort);
		List<String> clientPks = defaultClientKeys(n, nClients);
		ProcessConfig[] configsArray = new ProcessConfig[n];
		configs.toArray(configsArray);	
		List<Link> links = defaultLinks(n, configs);

		List<NodeService> services = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ProcessConfig config = configs.get(i);
			Link link = links.get(i);
			services.add(new NodeService(link, config, configsArray, clientPks));
		}

		return services;
	}

	private static Map<Integer, Deque<Slot>> genSlotMap(int n) {
		Map<Integer, Deque<Slot>> confirmedSlots = new ConcurrentHashMap<>();
		for (int i = 0; i < n; i++) {
			confirmedSlots.put(i, new LinkedBlockingDeque());
		}

		return confirmedSlots;
	}

	private static void printSlotMap(Map<Integer, Deque<Slot>> confirmedSlots) {
		for (int i = 0; i < confirmedSlots.size(); i++) {
			for (Slot s: confirmedSlots.get(i)) {
				System.out.printf("%d: %s\n", i, s);
			}
		}
	}

	@Test
	void singleExecutionTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10000;
		int clientId = n;
		int seq = 134;
		String cmd = "a";
		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		services.forEach(service -> service.startConsensus(clientId, seq, cmd));

		// FIXME (dsa): don't like this, but don't know how to do check
		// without assuming stuff about some correctness
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		services.forEach(service -> service.stopAndWait());

		printSlotMap(confirmedSlots);

		// Check output to clients is what was expected
		for (int i = 0; i < n; i++) {
			assertEquals(1, confirmedSlots.get(i).size());
			Slot s = confirmedSlots.get(i).removeFirst();
			assertEquals(s.getSlotId(), 1);
			assertEquals(s.getNonce(), nonce);
			assertEquals(s.getMessage(), cmd);
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 1);
			assertEquals(ledger.get(0), cmd);
		}
	}

	@Test
	void consecutiveExecutionTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10020;
		String nonce1 = "123";
		String cmd1 = "a";
		String nonce2 = "1234";
		String cmd2 = "b";

		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		services.forEach(service -> service.startConsensus(nonce1, cmd1));
		services.forEach(service -> service.startConsensus(nonce2, cmd2));

		// FIXME (dsa): don't like this, but don't know how to do check
		// without assuming stuff about some correctness
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		services.forEach(service -> service.stopAndWait());

		printSlotMap(confirmedSlots);

		// Check output to clients is what was expected
		for (int i = 0; i < n; i++) {
			assertEquals(2, confirmedSlots.get(i).size());
			Slot s1 = confirmedSlots.get(i).removeFirst();
			Slot s2 = confirmedSlots.get(i).removeFirst();
			assertEquals(nonce1, s1.getNonce());
			assertEquals(nonce2, s2.getNonce());
			assertEquals(cmd1, s1.getMessage());
			assertEquals(cmd2, s2.getMessage());
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 2);
			assertEquals(ledger.get(0), cmd1);
			assertEquals(ledger.get(1), cmd2);
		}
	}

	@Test
	void consecutiveExecutionDisagreementTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10040;
		String nonce1 = "123";
		String cmd1 = "a";
		String nonce2 = "1234";
		String cmd2 = "b";

		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		// Start consensus with different order - first half starts the first consensus instance with cmd1, second half with cmd2
		services.forEach(service -> {
			if (service.getId() < n/2) {
				service.startConsensus(nonce1, cmd1);
				service.startConsensus(nonce2, cmd2);
			} else {
				service.startConsensus(nonce2, cmd2);
				service.startConsensus(nonce1, cmd1);
			}
		});

		// FIXME (dsa): don't like this, but don't know how to do check
		// without assuming stuff about some correctness
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		services.forEach(service -> service.stopAndWait());

		printSlotMap(confirmedSlots);

		// Note that here acks can come out in either order

		// Check output to clients is what was expected
		assertEquals(2, confirmedSlots.get(0).size());
		Slot s1_0 = confirmedSlots.get(0).removeFirst();
		Slot s2_0 = confirmedSlots.get(0).removeFirst();
		for (int i = 1; i < n; i++) {
			assertEquals(2, confirmedSlots.get(i).size());
			Slot s1 = confirmedSlots.get(i).removeFirst();
			Slot s2 = confirmedSlots.get(i).removeFirst();

			assert(
				(s1_0.getClientId() == s1.getClientId() &&
				s1_0.getSeq() == s1.getSeq() &&
				s1_0.getMessage().equals(s1.getMessage()) &&
				s2_0.getClientId() == s2.getClientId() &&
				s2_0.getSeq() == s2.getSeq() &&
				s2_0.getMessage().equals(s2.getMessage()))
				||
				(s2_0.getClientId() == s1.getClientId() &&
				s2_0.getSeq() == s1.getSeq() &&
				s2_0.getMessage().equals(s1.getMessage()) &&
				s1_0.getClientId() == s2.getClientId() &&
				s1_0.getSeq() == s2.getSeq() &&
				s1_0.getMessage().equals(s2.getMessage()))
			);
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 2);
			assert(
				(ledger.get(0).equals(s1_0.getMessage()) &&
				ledger.get(1).equals(s2_0.getMessage()))
				||
				(ledger.get(1).equals(s1_0.getMessage()) &&
				ledger.get(0).equals(s2_0.getMessage()))
			);
		}

	}

	@Test
	void lateInputTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10060;
		int clientId1 = n;
		int seq1 = 12341;
		String cmd1 = "a";
		int clientId2 = n;
		int seq2 = 123;
		String cmd2 = "b";

		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		services.forEach(service -> {
			if (service.getId() != 0) {
				service.startConsensus(clientId1, seq1, cmd1);
				service.startConsensus(clientId2, seq2, cmd2);
			}
		});

		
		// FIXME (dsa): don't like this, but don't know how to do check
		// without assuming stuff about some correctness
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println("[test] all but 0 got somewhere");

		services.get(0).startConsensus(clientId2, seq2, cmd2);
		services.get(0).startConsensus(clientId1, seq1, cmd1);

		// FIXME (dsa): don't like this, but don't know how to do check
		// without assuming stuff about some correctness
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		services.forEach(service -> service.stopAndWait());

		printSlotMap(confirmedSlots);

		// Check output to clients is what was expected
		// Replica 0 is the only that might output in different order

		// Check replica 0
		Slot s1_0 = confirmedSlots.get(0).removeFirst();
		Slot s2_0 = confirmedSlots.get(0).removeFirst();
		assert(
				(nonce1.equals(s1_0.getNonce()) &&
				 cmd1.equals(s1_0.getMessage()) &&
				 nonce2.equals(s2_0.getNonce()) &&
				 cmd2.equals(s2_0.getMessage()))
				||
				(nonce2.equals(s1_0.getNonce()) &&
				 cmd2.equals(s1_0.getMessage()) &&
				 nonce1.equals(s2_0.getNonce()) &&
				 cmd1.equals(s2_0.getMessage()))
			  );

		// Check other replicas
		for (int i = 1; i < n; i++) {
			assertEquals(2, confirmedSlots.get(i).size());
			Slot s1 = confirmedSlots.get(i).removeFirst();
			Slot s2 = confirmedSlots.get(i).removeFirst();
			assertEquals(nonce1, s1.getNonce());
			assertEquals(nonce2, s2.getNonce());
			assertEquals(cmd1, s1.getMessage());
			assertEquals(cmd2, s2.getMessage());
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 2);
			assertEquals(ledger.get(0), cmd1);
			assertEquals(ledger.get(1), cmd2);
		}
	}

	// TODO: add tests with previous round messages
}
