package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;

import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;
import java.security.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;

public class HDSLedgerServiceTest {

	private AppendMessage createAppendRequestMessage(int id, int receiver, String value, int sequenceNumber) {
		AppendRequest appendRequest = new AppendRequest(value, sequenceNumber);

		AppendMessage message = new AppendMessage(id, Message.Type.APPEND_REQUEST, receiver);
		
		message.setMessage(new Gson().toJson(appendRequest));
        message.signSelf(String.format("/tmp/priv_%d.key", id));

		return message;
	}

	// n is set to 10 by default
	@BeforeAll
    public static void genKeys() {
		// Gen keys for servers
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

	private List<Link> defaultLinksClient(int n, List<ProcessConfig> clientConfigs, List<ProcessConfig> nodesConfigs) {
		return clientConfigs
				.stream()
				.map(config -> 
					new PerfectLink(config,
						config.getPort(),
						nodesConfigs.toArray(new ProcessConfig[n]),
						AppendMessage.class))
				.collect(Collectors.toList());
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

	List<NodeService> setupNodeServices(List<ProcessConfig> configs, List<Link> links, List<String> clientPks) {
		int n = configs.size();
		ProcessConfig[] configsArray = new ProcessConfig[n];
		configs.toArray(configsArray);	

		List<NodeService> services = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ProcessConfig config = configs.get(i);
			Link link = links.get(i);
			services.add(new NodeService(link, config, configsArray, clientPks));
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

	private List<String> defaultClientKeys(int n, int nClients) {
		return IntStream.range(n, n+nClients)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());
	}

	@Test
	void HDSLedgerStartsConsensusTest() {
		int n_Nodes = 4;
		int basePortNode = 20000;
		int n_Clients = n_Nodes;
		int basePortClient = 30000;
		int basePortHDS = 40000;
		int clientId = n_Nodes; // must be greater than n-1
		int seq = 0;
		String cmd = "a";
		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n_Nodes);

		// Setup node service
		List<ProcessConfig> nodeConfigs = defaultConfigs(n_Nodes, basePortNode);
		List<Link> nodeLinks = linksFromConfigs(nodeConfigs, ConsensusMessage.class);
		List<String> clientPks = defaultClientKeys(n_Nodes, n_Clients);
		List<NodeService> nodeServices = setupNodeServices(nodeConfigs, nodeLinks, clientPks);

		// Setup ledger service and client links
		List<ProcessConfig> ledgerConfigs = defaultConfigs(n_Nodes + n_Clients, basePortHDS);
		List<Link> ledgerLinks = linksFromConfigs(ledgerConfigs, AppendMessage.class);
		List<HDSLedgerService> HDSLedgerServices = setupHDSLedgerServices(n_Nodes, ledgerConfigs, ledgerLinks, nodeServices);

		Link clientLink = ledgerLinks.get(clientId);

		nodeServices.forEach(service -> service.listen());
		HDSLedgerServices.forEach(service -> service.listen());
		nodeServices.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		for (int i = 0; i < n_Nodes; i++) {
			AppendMessage request = createAppendRequestMessage(clientId, i, cmd, seq);
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
			Slot s = confirmedSlots.get(i).removeFirst();
			assertEquals(s.getSlotId(), 1);
			assertEquals(s.getClientId(), clientId);
			assertEquals(s.getSeq(), seq);
			assertEquals(s.getMessage(), cmd);
		}
		
		// Check state is what was expected
		for (NodeService service: nodeServices) {
			List<String> ledger = service.getLedger();
			assertEquals(ledger.size(), 1);
			assertEquals(ledger.get(0), cmd);
		}
	}

}
