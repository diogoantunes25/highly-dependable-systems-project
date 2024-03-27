package pt.ulisboa.tecnico.hdsledger.service.services;

import org.junit.jupiter.api.io.TempDir;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.GenesisFile;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.MessageCreator;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.service.ObserverAck;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;
import java.security.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.Gson;

public class HDSLedgerServiceTest {

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

	private static void defaultGenesisFile(String path, int replicas, int clients, int initialBalance) {
		boolean exists = GenesisFile.read(path);
		if (!exists) {
			try {

				Map<Integer, Integer> balances = new HashMap<>();

				for (int i = 0; i < replicas + clients; i++) {
					balances.put(i, initialBalance);
				}

				GenesisFile.write(path, balances);
			} catch (IOException e) {
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
	private List<Link> linksFromConfigs(List<ProcessConfig> configs, Class<? extends Message> messageClass) {
		int n = configs.size();
		return configs
				.stream()
				.map(config -> 
					new PerfectLink(config,
						config.getPort(),
						configs.toArray(new ProcessConfig[n]),
						messageClass))
				.collect(Collectors.toList());
	}

	List<NodeService> setupNodeServices(List<ProcessConfig> configs, List<Link> links, List<String> clientPks, String genesisFilePath) {
		int n = configs.size();
		ProcessConfig[] configsArray = new ProcessConfig[n];
		configs.toArray(configsArray);	

		List<NodeService> services = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ProcessConfig config = configs.get(i);
			Link link = links.get(i);
			services.add(new NodeService(link, config, configsArray, clientPks, genesisFilePath));
		}

		return services;
	}

	List<HDSLedgerService> setupHDSLedgerServices(int n, List<ProcessConfig> configs, List<Link> links, List<NodeService> nodeServices) {
		ProcessConfig[] configsArray = new ProcessConfig[configs.size()];
		configs.toArray(configsArray);	

		List<HDSLedgerService> services = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ProcessConfig config = configs.get(i);
			NodeService nodeService = nodeServices.get(i);
			services.add(new HDSLedgerService(configsArray, links.get(i), config, nodeService));
		}

		return services;
	}

	private static Map<Integer, Deque<Confirmation>> genSlotMap(int n) {
		Map<Integer, Deque<Confirmation>> confirmedSlots = new ConcurrentHashMap<>();
		for (int i = 0; i < n; i++) {
			confirmedSlots.put(i, new LinkedBlockingDeque());
		}

		return confirmedSlots;
	}

	private static Map<Integer, Deque<Boolean>> genSuccessMap(int n) {
		Map<Integer, Deque<Boolean>> success = new ConcurrentHashMap<>();
		for (int i = 0; i < n; i++) {
			success.put(i, new LinkedBlockingDeque());
		}

		return success;
	}

	private static void printSlotMap(Map<Integer, Deque<Confirmation>> confirmedSlots) {
		for (int i = 0; i < confirmedSlots.size(); i++) {
			for (Confirmation s: confirmedSlots.get(i)) {
				System.out.printf("%d: %s\n", i, s);
			}
		}
	}

	private List<String> defaultClientKeys(int n, int nClients) {
		return IntStream.range(n, n+nClients)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());
	}

	private LedgerMessage createNoSignedTransferRequest(int requestId, int source, int destination, int amount) {
        // TODO: make this consistent with the way this was done before
        String sourcePublicKey = String.format("/tmp/pub_%d.key", source);
        String destinationPublicKey = String.format("/tmp/pub_%d.key", destination);
        TransferRequest transferRequest = new TransferRequest(sourcePublicKey, destinationPublicKey, amount);

        LedgerMessage ledgerMessage = new LedgerMessage(source, Message.Type.TRANSFER_REQUEST);
        ledgerMessage.setMessage(new Gson().toJson(transferRequest));
        ledgerMessage.setSequenceNumber(requestId);
        //ledgerMessage.signSelf(String.format("/tmp/priv_%d.key", source));

        return ledgerMessage;
    }

	private LedgerMessage createClientIdTransferRequest(int clientId,int requestId, int source, int destination, int amount) {
        // TODO: make this consistent with the way this was done before
        String sourcePublicKey = String.format("/tmp/pub_%d.key", source);
        String destinationPublicKey = String.format("/tmp/pub_%d.key", destination);
        TransferRequest transferRequest = new TransferRequest(sourcePublicKey, destinationPublicKey, amount);

        LedgerMessage ledgerMessage = new LedgerMessage(clientId, Message.Type.TRANSFER_REQUEST);
        ledgerMessage.setMessage(new Gson().toJson(transferRequest));
        ledgerMessage.setSequenceNumber(requestId);
        //ledgerMessage.signSelf(String.format("/tmp/priv_%d.key", source));

        return ledgerMessage;
    }

	private LedgerMessage createBadSignedTransferRequest(int requestId, int source, int destination, int amount, int signature) {
        // TODO: make this consistent with the way this was done before
        String sourcePublicKey = String.format("/tmp/pub_%d.key", source);
        String destinationPublicKey = String.format("/tmp/pub_%d.key", destination);
        TransferRequest transferRequest = new TransferRequest(sourcePublicKey, destinationPublicKey, amount);

        LedgerMessage ledgerMessage = new LedgerMessage(source, Message.Type.TRANSFER_REQUEST);
        ledgerMessage.setMessage(new Gson().toJson(transferRequest));
        ledgerMessage.setSequenceNumber(requestId);
        ledgerMessage.signSelf(String.format("/tmp/priv_%d.key", signature));

        return ledgerMessage;
    }

	// Returns hash of public key with id i
	private static String numberToId(int i) {
		return SigningUtils.publicKeyHash(String.format("/tmp/pub_%d.key", i));
	}

	@Test
	void HDSLedgerStartsConsensusTest(@TempDir Path tempDir) {
		int n_Nodes = 4;
		int basePortNode = 20000;
		int n_Clients = 2;
		int basePortClient = 30000;
		int basePortHDS = 40000;
		int clientId = n_Nodes; // must be greater than n-1
		int clientId2 = n_Nodes+1; // must be greater than n-1
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);
		int seq = 0;
		int amount = 10;
		int initial = 15;

		String genesisFilePath = tempDir.resolve("genesis.json").toString();
		defaultGenesisFile(genesisFilePath, n_Nodes, n_Clients, initial);
		Map<Integer, Deque<Confirmation>> confirmedSlots = genSlotMap(n_Nodes);

		// Setup node service
		List<ProcessConfig> nodeConfigs = defaultConfigs(n_Nodes, basePortNode);
		List<Link> nodeLinks = linksFromConfigs(nodeConfigs, ConsensusMessage.class);
		List<String> clientPks = defaultClientKeys(n_Nodes, n_Clients);
		List<NodeService> nodeServices = setupNodeServices(nodeConfigs, nodeLinks, clientPks, genesisFilePath);

		// Setup ledger service and client links
		List<ProcessConfig> ledgerConfigs = defaultConfigs(n_Nodes + n_Clients, basePortHDS);
		List<Link> ledgerLinks = linksFromConfigs(ledgerConfigs, LedgerMessage.class);
		List<HDSLedgerService> HDSLedgerServices = setupHDSLedgerServices(n_Nodes, ledgerConfigs, ledgerLinks, nodeServices);

		Link clientLink = ledgerLinks.get(clientId);

		nodeServices.forEach(service -> service.listen());
		HDSLedgerServices.forEach(service -> service.listen());
		nodeServices.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cidOpt, seqNOpt, slotIdOpt) ->
				confirmedSlots.get(id).add(new Confirmation(cidOpt, seqNOpt, slotIdOpt.get()));
			service.registerObserver(observer);
		});

		for (int i = 0; i < n_Nodes; i++) {
			LedgerMessage request = MessageCreator.createTransferRequest(seq, clientId, clientId2, amount);
			clientLink.send(i, request);
		}

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		nodeServices.forEach(service -> service.stopAndWait());

		printSlotMap(confirmedSlots);

		// Check output to clients is what was expected
		for (int i = 0; i < n_Nodes; i++) {
			assertEquals(1, confirmedSlots.get(i).size());
			Confirmation s = confirmedSlots.get(i).removeFirst();

			assertEquals(s.slotId, 1);
			assertEquals(s.clientId, clientId);
			assertEquals(s.seq, seq);
		}
		
		// Check state is what was expected
		for (NodeService service: nodeServices) {
			Map<String, Integer> ledger = service.getLedger();
			assertEquals(ledger.get(clientHashPk), initial - amount - NodeService.DEFAULT_FEE);
			assertEquals(ledger.get(clientHashPk2), initial + amount);
		}
	}

	@Test
	void HDSLedgerNoSignedClientTest(@TempDir Path tempDir) {
		int n_Nodes = 4;
		int basePortNode = 20200;
		int n_Clients = 2;
		int basePortClient = 30200;
		int basePortHDS = 40200;
		int clientId = n_Nodes; // must be greater than n-1
		int clientId2 = n_Nodes+1; // must be greater than n-1
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);
		int seq = 0;
		int amount = 10;
		int initial1 = 15;
		int initial2 = 15;

		Map<Integer, Deque<Boolean>> success = genSuccessMap(n_Nodes);

		String genesisFilePath = tempDir.resolve("genesis.json").toString();
		defaultGenesisFile(genesisFilePath, n_Nodes, n_Clients, initial1);

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);

		// Setup node service
		List<ProcessConfig> nodeConfigs = defaultConfigs(n_Nodes, basePortNode);
		List<Link> nodeLinks = linksFromConfigs(nodeConfigs, ConsensusMessage.class);
		List<String> clientPks = defaultClientKeys(n_Nodes, n_Clients);
		List<NodeService> nodeServices = setupNodeServices(nodeConfigs, nodeLinks, clientPks, genesisFilePath);

		// Setup ledger service and client links
		List<ProcessConfig> ledgerConfigs = defaultConfigs(n_Nodes + n_Clients, basePortHDS);
		List<Link> ledgerLinks = linksFromConfigs(ledgerConfigs, LedgerMessage.class);
		List<HDSLedgerService> HDSLedgerServices = setupHDSLedgerServices(n_Nodes, ledgerConfigs, ledgerLinks, nodeServices);

		Link clientLink = ledgerLinks.get(clientId);

		nodeServices.forEach(service -> service.listen());
		HDSLedgerServices.forEach(service -> service.listen());

		HDSLedgerServices.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cidOpt, seqNOpt, slotIdOpt) ->
				success.get(id).add(!slotIdOpt.isEmpty());
			service.registerObserver(observer);
		});


		for (int i = 0; i < n_Nodes; i++) {
			LedgerMessage request = createNoSignedTransferRequest(seq, clientId, clientId2, amount);
			clientLink.send(i, request);
		}

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		HDSLedgerServices.forEach(service -> service.stopAndWait());


		// Verifies no success in this transfer
		for (int i = 0; i < n_Nodes; i++) {
			Boolean success_value = success.get(i).removeFirst();
			assertFalse(success_value);
		}
		
	}

	@Test
	void HDSLedgerBadSignatureTest(@TempDir Path tempDir) {
		int n_Nodes = 4;
		int basePortNode = 20300;
		int n_Clients = 2;
		int basePortClient = 30300;
		int basePortHDS = 40300;
		int clientId = n_Nodes; // must be greater than n-1
		int clientId2 = n_Nodes+1; // must be greater than n-1
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);
		int seq = 0;
		int amount = 10;
		int initial1 = 15;
		int initial2 = 15;

		Map<Integer, Deque<Boolean>> success = genSuccessMap(n_Nodes);

		String genesisFilePath = tempDir.resolve("genesis.json").toString();
		defaultGenesisFile(genesisFilePath, n_Nodes, n_Clients, initial1);

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);

		// Setup node service
		List<ProcessConfig> nodeConfigs = defaultConfigs(n_Nodes, basePortNode);
		List<Link> nodeLinks = linksFromConfigs(nodeConfigs, ConsensusMessage.class);
		List<String> clientPks = defaultClientKeys(n_Nodes, n_Clients);
		List<NodeService> nodeServices = setupNodeServices(nodeConfigs, nodeLinks, clientPks, genesisFilePath);

		// Setup ledger service and client links
		List<ProcessConfig> ledgerConfigs = defaultConfigs(n_Nodes + n_Clients, basePortHDS);
		List<Link> ledgerLinks = linksFromConfigs(ledgerConfigs, LedgerMessage.class);
		List<HDSLedgerService> HDSLedgerServices = setupHDSLedgerServices(n_Nodes, ledgerConfigs, ledgerLinks, nodeServices);

		Link clientLink = ledgerLinks.get(clientId);

		nodeServices.forEach(service -> service.listen());
		HDSLedgerServices.forEach(service -> service.listen());

		HDSLedgerServices.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cidOpt, seqNOpt, slotIdOpt) ->
				success.get(id).add(!slotIdOpt.isEmpty());
			service.registerObserver(observer);
		});


		for (int i = 0; i < n_Nodes; i++) {
			LedgerMessage request = createBadSignedTransferRequest(seq, clientId, clientId2, amount, clientId2);
			clientLink.send(i, request);
		}

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		HDSLedgerServices.forEach(service -> service.stopAndWait());


		// Verifies no success in this transfer
		for (int i = 0; i < n_Nodes; i++) {
			Boolean success_value = success.get(i).removeFirst();
			assertFalse(success_value);
		}
		
	}

	@Test
	void HDSLedgerImpersonateTest(@TempDir Path tempDir) {
		int n_Nodes = 4;
		int basePortNode = 20400;
		int n_Clients = 2;
		int basePortClient = 30400;
		int basePortHDS = 40400;
		int clientId = n_Nodes; // must be greater than n-1
		int clientId2 = n_Nodes+1; // must be greater than n-1
		int clientId3 = n_Nodes+2; // must be greater than n-1
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);
		String clientHashPk3 = numberToId(clientId3);
		int seq = 0;
		int amount = 10;
		int initial1 = 15;
		int initial2 = 15;
		int initial3 = 15;

		Map<Integer, Deque<Boolean>> success = genSuccessMap(n_Nodes);

		String genesisFilePath = tempDir.resolve("genesis.json").toString();
		defaultGenesisFile(genesisFilePath, n_Nodes, n_Clients, initial1);

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);
		genesisBlock.put(clientHashPk3, initial3);

		// Setup node service
		List<ProcessConfig> nodeConfigs = defaultConfigs(n_Nodes, basePortNode);
		List<Link> nodeLinks = linksFromConfigs(nodeConfigs, ConsensusMessage.class);
		List<String> clientPks = defaultClientKeys(n_Nodes, n_Clients);
		List<NodeService> nodeServices = setupNodeServices(nodeConfigs, nodeLinks, clientPks, genesisFilePath);

		// Setup ledger service and client links
		List<ProcessConfig> ledgerConfigs = defaultConfigs(n_Nodes + n_Clients, basePortHDS);
		List<Link> ledgerLinks = linksFromConfigs(ledgerConfigs, LedgerMessage.class);
		List<HDSLedgerService> HDSLedgerServices = setupHDSLedgerServices(n_Nodes, ledgerConfigs, ledgerLinks, nodeServices);

		Link clientLink = ledgerLinks.get(clientId);

		nodeServices.forEach(service -> service.listen());
		HDSLedgerServices.forEach(service -> service.listen());

		HDSLedgerServices.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cidOpt, seqNOpt, slotIdOpt) ->
				success.get(id).add(!slotIdOpt.isEmpty());
			service.registerObserver(observer);
		});


		for (int i = 0; i < n_Nodes; i++) {
			LedgerMessage request = createClientIdTransferRequest(clientId, seq, clientId2, clientId3, amount);
			clientLink.send(i, request);
		}

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		HDSLedgerServices.forEach(service -> service.stopAndWait());


		// Verifies no success in this transfer
		for (int i = 0; i < n_Nodes; i++) {
			Boolean success_value = success.get(i).removeFirst();
			assertFalse(success_value);
		}
		
	}

	@Test
	void HDSLedgerNotEnoughMoneyTest(@TempDir Path tempDir) {
		int n_Nodes = 4;
		int basePortNode = 20500;
		int n_Clients = 2;
		int basePortClient = 30500;
		int basePortHDS = 40500;
		int clientId = n_Nodes; // must be greater than n-1
		int clientId2 = n_Nodes+1; // must be greater than n-1
		String clientHashPk = numberToId(clientId);
		String clientHashPk2 = numberToId(clientId2);
		int seq = 0;
		int amount = 20;
		int initial1 = 15;
		int initial2 = 15;

		Map<Integer, Deque<Boolean>> success = genSuccessMap(n_Nodes);

		String genesisFilePath = tempDir.resolve("genesis.json").toString();
		defaultGenesisFile(genesisFilePath, n_Nodes, n_Clients, initial1);

		Map<String, Integer> genesisBlock = new HashMap<>();
		genesisBlock.put(clientHashPk, initial1);
		genesisBlock.put(clientHashPk2, initial2);

		// Setup node service
		List<ProcessConfig> nodeConfigs = defaultConfigs(n_Nodes, basePortNode);
		List<Link> nodeLinks = linksFromConfigs(nodeConfigs, ConsensusMessage.class);
		List<String> clientPks = defaultClientKeys(n_Nodes, n_Clients);
		List<NodeService> nodeServices = setupNodeServices(nodeConfigs, nodeLinks, clientPks, genesisFilePath);

		// Setup ledger service and client links
		List<ProcessConfig> ledgerConfigs = defaultConfigs(n_Nodes + n_Clients, basePortHDS);
		List<Link> ledgerLinks = linksFromConfigs(ledgerConfigs, LedgerMessage.class);
		List<HDSLedgerService> HDSLedgerServices = setupHDSLedgerServices(n_Nodes, ledgerConfigs, ledgerLinks, nodeServices);

		Link clientLink = ledgerLinks.get(clientId);

		nodeServices.forEach(service -> service.listen());
		HDSLedgerServices.forEach(service -> service.listen());

		HDSLedgerServices.forEach(service -> {
			final int id = service.getId();
			ObserverAck observer = (cidOpt, seqNOpt, slotIdOpt) ->
				success.get(id).add(!slotIdOpt.isEmpty());
			service.registerObserver(observer);
		});


		for (int i = 0; i < n_Nodes; i++) {
			LedgerMessage request = MessageCreator.createTransferRequest(seq, clientId, clientId2, amount);
			clientLink.send(i, request);
		}

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		HDSLedgerServices.forEach(service -> service.stopAndWait());


		// Verifies no success in this transfer
		for (int i = 0; i < n_Nodes; i++) {
			Boolean success_value = success.get(i).removeFirst();
			assertFalse(success_value);
		}
		
	}


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
