package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.service.Command;
import pt.ulisboa.tecnico.hdsledger.service.StringCommand;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;

public class NodeServiceTest {

	// n is set to 10 by default
	@BeforeAll
    public static void genKeys() {
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
					new PerfectLink(config,
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

	private static AppendMessage getDefaultProof(int clientId, int seq, String cmd) {
		AppendRequest appendRequest = new AppendRequest(cmd, seq);

        AppendMessage message = new AppendMessage(clientId, Message.Type.APPEND_REQUEST, 0);
        message.setMessage(new Gson().toJson(appendRequest));
        message.signSelf(String.format("/tmp/priv_%d.key", clientId));

        return message;

	}

	@Test
	void singleExecutionTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10000;
		int clientId = n;
		int seq = 134;
		String value = "a";
		AppendMessage proof = getDefaultProof(clientId, seq, value);
		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		services.forEach(service -> service.startConsensus(clientId, seq, value, proof));

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
			Slot<StringCommand> s = confirmedSlots.get(i).removeFirst();
			assertEquals(s.getSlotId(), 1);
			StringCommand cmd = s.getCmd();
			assertEquals(cmd.getClientId(), clientId);
			assertEquals(cmd.getSeq(), seq);
			assertEquals(cmd.getValue(), value);
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 1);
			assertEquals(ledger.get(0), value);
		}
	}

	@Test
	void consecutiveExecutionTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10020;

		int clientId1 = n;
		int seq1 = 13241;
		String value1 = "a";
		AppendMessage proof1 = getDefaultProof(clientId1, seq1, value1);

		int clientId2 = n;
		int seq2 = 13223;
		String value2 = "b";
		AppendMessage proof2 = getDefaultProof(clientId2, seq2, value2);


		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		services.forEach(service -> service.startConsensus(clientId1, seq1, value1, proof1));
		services.forEach(service -> service.startConsensus(clientId2, seq2, value2, proof2));

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
			Slot<StringCommand> s1 = confirmedSlots.get(i).removeFirst();
			Slot<StringCommand> s2 = confirmedSlots.get(i).removeFirst();
			StringCommand cmd1 = s1.getCmd();
			StringCommand cmd2 = s2.getCmd();

			assertEquals(clientId1, cmd1.getClientId());
			assertEquals(seq1, cmd1.getSeq());
			assertEquals(value1, cmd1.getValue());

			assertEquals(clientId2, cmd2.getClientId());
			assertEquals(seq2, cmd2.getSeq());
			assertEquals(value2, cmd2.getValue());
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 2);
			assertEquals(ledger.get(0), value1);
			assertEquals(ledger.get(1), value2);
		}
	}

	@Test
	void consecutiveExecutionDisagreementTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10040;

		int clientId1 = n;
		int seq1 = 13241;
		String value1 = "a";
		AppendMessage proof1 = getDefaultProof(clientId1, seq1, value1);

		int clientId2 = n;
		int seq2 = 13223;
		String value2 = "b";
		AppendMessage proof2 = getDefaultProof(clientId2, seq2, value2);

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
				service.startConsensus(clientId1, seq1, value1, proof1);
				service.startConsensus(clientId2, seq2, value2, proof2);
			} else {
				service.startConsensus(clientId2, seq2, value2, proof2);
				service.startConsensus(clientId1, seq1, value1, proof1);
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
		Slot<StringCommand> s1_0 = confirmedSlots.get(0).removeFirst();
		Slot<StringCommand> s2_0 = confirmedSlots.get(0).removeFirst();
		StringCommand cmd1_0 = s1_0.getCmd();
		StringCommand cmd2_0 = s2_0.getCmd();

		for (int i = 1; i < n; i++) {
			assertEquals(2, confirmedSlots.get(i).size());
			Slot<StringCommand> s1 = confirmedSlots.get(i).removeFirst();
			Slot<StringCommand> s2 = confirmedSlots.get(i).removeFirst();
			StringCommand cmd1 = s1.getCmd();
			StringCommand cmd2 = s2.getCmd();

			assert(
				(cmd1_0.getClientId() == cmd1.getClientId() &&
				cmd1_0.getSeq() == cmd1.getSeq() &&
				cmd1_0.getValue().equals(cmd1.getValue()) &&
				cmd2_0.getClientId() == cmd2.getClientId() &&
				cmd2_0.getSeq() == cmd2.getSeq() &&
				cmd2_0.getValue().equals(cmd2.getValue()))
				||
				(cmd2_0.getClientId() == cmd1.getClientId() &&
				cmd2_0.getSeq() == cmd1.getSeq() &&
				cmd2_0.getValue().equals(cmd1.getValue()) &&
				cmd1_0.getClientId() == cmd2.getClientId() &&
				cmd1_0.getSeq() == cmd2.getSeq() &&
				cmd1_0.getValue().equals(cmd2.getValue()))
			);
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 2);
			assert(
				(ledger.get(0).equals(cmd1_0.getValue()) &&
				ledger.get(1).equals(cmd2_0.getValue()))
				||
				(ledger.get(1).equals(cmd1_0.getValue()) &&
				ledger.get(0).equals(cmd2_0.getValue()))
			);
		}

	}

	@Test
	void lateInputTest() {
		int n = 4;
		int nClients = 1;
		int basePort = 10060;

		int clientId1 = n;
		int seq1 = 13241;
		String value1 = "a";
		AppendMessage proof1 = getDefaultProof(clientId1, seq1, value1);

		int clientId2 = n;
		int seq2 = 13223;
		String value2 = "b";
		AppendMessage proof2 = getDefaultProof(clientId2, seq2, value2);

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
				service.startConsensus(clientId1, seq1, value1, proof1);
				service.startConsensus(clientId2, seq2, value2, proof2);
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

		services.get(0).startConsensus(clientId2, seq2, value2, proof2);
		services.get(0).startConsensus(clientId1, seq1, value1, proof1);

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
		Slot<StringCommand> s1_0 = confirmedSlots.get(0).removeFirst();
		Slot<StringCommand> s2_0 = confirmedSlots.get(0).removeFirst();
		StringCommand cmd1_0 = s1_0.getCmd();
		StringCommand cmd2_0 = s2_0.getCmd();

		assert(
				(seq1 == cmd1_0.getSeq() &&
				 clientId1 == cmd1_0.getClientId() &&
				 value1.equals(cmd1_0.getValue()) &&
				 seq2 == cmd2_0.getSeq() &&
				 clientId2 == cmd2_0.getClientId() &&
				 value2.equals(cmd2_0.getValue())
				||
				(seq2 == cmd1_0.getSeq()) &&
				 clientId2 == cmd1_0.getClientId() &&
				 value2.equals(cmd1_0.getValue()) &&
				 seq1 == cmd2_0.getSeq() &&
				 clientId1 == cmd2_0.getClientId() &&
				 value1.equals(cmd2_0.getValue()))
			  );

		// Check other replicas
		for (int i = 1; i < n; i++) {
			assertEquals(2, confirmedSlots.get(i).size());
			Slot<StringCommand> s1 = confirmedSlots.get(i).removeFirst();
			Slot<StringCommand> s2 = confirmedSlots.get(i).removeFirst();
			StringCommand cmd1 = s1.getCmd();
			StringCommand cmd2 = s2.getCmd();
			assertEquals(seq1, cmd1.getSeq());
			assertEquals(seq2, cmd2.getSeq());
			assertEquals(clientId1, cmd1.getClientId());
			assertEquals(clientId2, cmd2.getClientId());
			assertEquals(value1, cmd1.getValue());
			assertEquals(value2, cmd2.getValue());
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 2);
			assertEquals(ledger.get(0), value1);
			assertEquals(ledger.get(1), value2);
		}
	}

	// TODO: add tests with previous round messages
	// TODO: add tests where client sends bad signatures
	// TODO: add tests where client sends same thing twice
}
