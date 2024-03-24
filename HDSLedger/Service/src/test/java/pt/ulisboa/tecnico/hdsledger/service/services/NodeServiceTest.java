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
import pt.ulisboa.tecnico.hdsledger.service.ObserverAck;
import pt.ulisboa.tecnico.hdsledger.service.BankCommand;
import pt.ulisboa.tecnico.hdsledger.service.BankState;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.MessageCreator;

import java.security.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;

public class NodeServiceTest {

	// n is set to 10 by default
	@BeforeAll
	public static void genKeys() throws GeneralSecurityException, IOException {
		int n = 10;
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

	List<NodeService> setupServices(int n, int basePort, int nClients, Map<String, Integer> genesisBlock) {
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

		services.forEach(s -> s.genesis(genesisBlock));

		return services;
	}

	private static Map<Integer, Deque<Confirmation>> genSlotMap(int n) {
		Map<Integer, Deque<Confirmation>> confirmedSlots = new ConcurrentHashMap<>();
		for (int i = 0; i < n; i++) {
			confirmedSlots.put(i, new LinkedBlockingDeque<>());
		}

		return confirmedSlots;
	}

	private static void printSlotMap(Map<Integer, Deque<Confirmation>> confirmedSlots) {
		for (int i = 0; i < confirmedSlots.size(); i++) {
			for (Confirmation s: confirmedSlots.get(i)) {
				System.out.printf("%d: %s\n", i, s);
			}
		}
	}

	// Returns hash of public key with id i
	private static String numberToId(int i) {
		return SigningUtils.publicKeyHash(String.format("/tmp/pub_%d.key", i));
	}

	@Test
	void singleExecutionTest() {
		int n = 4;
		int nClients = 2;
		int basePort = 9000;
		int clientId = n;
		int clientId2 = n+1;

		String clientPk = String.format("/tmp/pub_%d.key", clientId);
		String clientPk2 = String.format("/tmp/pub_%d.key", clientId2);
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);

		int seq = 134;
		int amount = 10;
		int initial1 = 15;
		int initial2 = 15;

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);

		LedgerMessage proof = MessageCreator.createTransferRequest(seq, clientId, clientId2, amount);

		Map<Integer, Deque<Confirmation>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients, genesisBlock);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cid, seqN, slotId) ->
				confirmedSlots.get(id).add(new Confirmation(cid, seqN, slotId.get()));
			service.registerObserver(observer);
		});

		services.forEach(service -> service.startConsensus(clientId, seq, clientPk, clientPk2, amount, proof));

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
			Confirmation s = confirmedSlots.get(i).removeFirst();
			assertEquals(s.slotId, 1);
			assertEquals(s.clientId, clientId);
			assertEquals(s.seq, seq);
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			Map<String, Integer> ledger = service.getLedger();
			assertEquals(ledger.get(clientHashPk), initial1 - amount);
			assertEquals(ledger.get(clientHashPk2), initial2 + amount);
		}
	}

	@Test
	void consecutiveExecutionTest() {
		int n = 4;
		int nClients = 2;
		int basePort = 8000;
		int clientId = n;
		int clientId2 = n+1;

		String clientPk = String.format("/tmp/pub_%d.key", clientId);
		String clientPk2 = String.format("/tmp/pub_%d.key", clientId2);
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);

		int initial1 = 15;
		int initial2 = 15;

		int seq1 = 134;
		int amount1 = 2;
		LedgerMessage proof1 = MessageCreator.createTransferRequest(seq1, clientId, clientId2, amount1);

		int seq2 = 134;
		int amount2 = 5;
		LedgerMessage proof2 = MessageCreator.createTransferRequest(seq2, clientId, clientId2, amount2);

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);

		Map<Integer, Deque<Confirmation>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients, genesisBlock);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cid, seqN, slotId) ->
				confirmedSlots.get(id).add(new Confirmation(cid, seqN, slotId.get()));
			service.registerObserver(observer);
		});

		services.forEach(service -> service.startConsensus(clientId, seq1, clientPk, clientPk2, amount1, proof1));
		services.forEach(service -> service.startConsensus(clientId, seq2, clientPk, clientPk2, amount2, proof2));

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
			Confirmation s1 = confirmedSlots.get(i).removeFirst();
			Confirmation s2 = confirmedSlots.get(i).removeFirst();

			assertEquals(s1.slotId, 1);
			assertEquals(s1.clientId, clientId);
			assertEquals(s1.seq, seq1);

			assertEquals(s2.slotId, 2);
			assertEquals(s2.clientId, clientId);
			assertEquals(s2.seq, seq2);
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			Map<String, Integer> ledger = service.getLedger();
			assertEquals(ledger.get(clientHashPk), initial1 - amount1 - amount2);
			assertEquals(ledger.get(clientHashPk2), initial2 + amount1 + amount2);
		}
	}

	@Test
	void consecutiveExecutionDisagreementTest() {
		int n = 4;
		int nClients = 2;
		int basePort = 10000;
		int clientId = n;
		int clientId2 = n+1;

		String clientPk = String.format("/tmp/pub_%d.key", clientId);
		String clientPk2 = String.format("/tmp/pub_%d.key", clientId2);
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);

		int initial1 = 15;
		int initial2 = 15;

		int seq1 = 134;
		int amount1 = 2;
		LedgerMessage proof1 = MessageCreator.createTransferRequest(seq1, clientId, clientId2, amount1);

		int seq2 = 134;
		int amount2 = 5;
		LedgerMessage proof2 = MessageCreator.createTransferRequest(seq2, clientId, clientId2, amount2);

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);

		Map<Integer, Deque<Confirmation>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients, genesisBlock);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cid, seqN, slotId) ->
				confirmedSlots.get(id).add(new Confirmation(cid, seqN, slotId.get()));
			service.registerObserver(observer);
		});

		services.forEach(service -> {
			if (service.getId() < n/2) {
				service.startConsensus(clientId, seq1, clientPk, clientPk2, amount1, proof1);
				service.startConsensus(clientId, seq2, clientPk, clientPk2, amount2, proof2);
			} else {
				service.startConsensus(clientId, seq2, clientPk, clientPk2, amount2, proof2);
				service.startConsensus(clientId, seq1, clientPk, clientPk2, amount1, proof1);
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

		// Check output to clients is what was expected
		for (int i = 0; i < n; i++) {
			assertEquals(2, confirmedSlots.get(i).size());
			Confirmation s1 = confirmedSlots.get(i).removeFirst();
			Confirmation s2 = confirmedSlots.get(i).removeFirst();

			assertEquals(s1.clientId, clientId);
			assertEquals(s2.clientId, clientId);

			// Since two clients submited in parallel, confirmations can come
			// in any order
			assert((s1.slotId == 1 && s2.slotId == 2) || (s1.slotId == 2 && s2.slotId == 1));
			assert((s1.seq == seq1 && s2.seq == seq2) || (s1.seq == seq2 && s2.seq == seq1));
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			Map<String, Integer> ledger = service.getLedger();
			assertEquals(ledger.get(clientHashPk), initial1 - amount1 - amount2);
			assertEquals(ledger.get(clientHashPk2), initial2 + amount1 + amount2);
		}
	}

	@Test
	void lateInputTest() {
		int n = 4;
		int nClients = 2;
		int basePort = 7000;
		int clientId = n;
		int clientId2 = n+1;

		String clientPk = String.format("/tmp/pub_%d.key", clientId);
		String clientPk2 = String.format("/tmp/pub_%d.key", clientId2);
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);

		int initial1 = 15;
		int initial2 = 15;

		int seq1 = 134;
		int amount1 = 2;
		LedgerMessage proof1 = MessageCreator.createTransferRequest(seq1, clientId, clientId2, amount1);

		int seq2 = 134;
		int amount2 = 5;
		LedgerMessage proof2 = MessageCreator.createTransferRequest(seq2, clientId, clientId2, amount2);

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);

		Map<Integer, Deque<Confirmation>> confirmedSlots = genSlotMap(n);

		List<NodeService> services = setupServices(n, basePort, nClients, genesisBlock);
		services.forEach(service -> service.listen());
		services.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cid, seqN, slotId) ->
				confirmedSlots.get(id).add(new Confirmation(cid, seqN, slotId.get()));
			service.registerObserver(observer);
		});

		services.forEach(service -> {
			if (service.getId() != 0) service.startConsensus(clientId, seq1, clientPk, clientPk2, amount1, proof1);
		});

		services.forEach(service -> {
			if (service.getId() != 0) service.startConsensus(clientId, seq2, clientPk, clientPk2, amount2, proof2);
		});

		// FIXME (dsa): don't like this, but don't know how to do check
		// without assuming stuff about some correctness
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		services.get(0).startConsensus(clientId, seq2, clientPk, clientPk2, amount2, proof2);
		services.get(0).startConsensus(clientId, seq1, clientPk, clientPk2, amount1, proof1);

		// FIXME (dsa): don't like this, but don't know how to do check
		// without assuming stuff about some correctness
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		services.forEach(service -> service.stopAndWait());

		printSlotMap(confirmedSlots);


		// First replica output does not have guaranteed order
		assertEquals(2, confirmedSlots.get(0).size());
		Confirmation s1_0 = confirmedSlots.get(0).removeFirst();
		Confirmation s2_0 = confirmedSlots.get(0).removeFirst();
		assert((s1_0.slotId == 1 && s2_0.slotId == 2) ||
				(s1_0.slotId == 2 && s2_0.slotId == 1));

		// Check output to clients is what was expected
		for (int i = 1; i < n; i++) {
			assertEquals(2, confirmedSlots.get(i).size());
			Confirmation s1 = confirmedSlots.get(i).removeFirst();
			Confirmation s2 = confirmedSlots.get(i).removeFirst();

			assertEquals(s1.clientId, clientId);
			assertEquals(s1.seq, seq1);
			assertEquals(1, s1.slotId);

			assertEquals(s2.clientId, clientId);
			assertEquals(s2.seq, seq2);
			assertEquals(2, s2.slotId);
		}
		
		// Check state is what was expected
		for (NodeService service: services) {
			Map<String, Integer> ledger = service.getLedger();
			assertEquals(ledger.get(clientHashPk), initial1 - amount1 - amount2);
			assertEquals(ledger.get(clientHashPk2), initial2 + amount1 + amount2);
		}

	}

	// TODO: add tests with previous round messages
	// TODO: add tests where client sends bad signatures
	// TODO: add tests where client sends same thing twice
	
	private class Confirmation {
		int clientId;
		int seq;
		int slotId;

		Confirmation(int clientId, int seq, int slotId) {
			this.clientId = clientId;
			this.seq = seq;
			this.slotId = slotId;
		}

	@Override
	public String toString() {
        return "Confirmation{" +
                "clientId=" + clientId +
                ", seq=" + seq +
                ", slotId=" + slotId +
                '}';
    }
	}
}
