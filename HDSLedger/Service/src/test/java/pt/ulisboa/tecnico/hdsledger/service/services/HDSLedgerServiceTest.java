package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.consensus.message.*;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.APLink;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.Slot;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HDSLedgerServiceTest {

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
						Message.class))
				.collect(Collectors.toList());
	}

	private List<Link> defaultLinksClient(int n, List<ProcessConfig> configs, List<ProcessConfig> nodesConfigs) {
		ProcessConfig[] configsArray = new ProcessConfig[n];
		configs.toArray(configsArray);	
		return configs
				.stream()
				.map(config -> 
					new APLink(config,
						config.getPort(),
						nodesConfigs.toArray(new ProcessConfig[n]),
						Message.class))
				.collect(Collectors.toList());
	}

	List<NodeService> setupNodeServices(int n, int basePort) {
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

	List<HDSLedgerService> setupHDSLedgerServices(int n, int basePort, ProcessConfig[] clientsConfigs, List<NodeService> nodeServices) {
		List<ProcessConfig> configs = defaultConfigs(n, basePort);
		ProcessConfig[] configsArray = new ProcessConfig[n];
		configs.toArray(configsArray);	

		List<HDSLedgerService> services = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ProcessConfig config = configs.get(i);
			Link link = new APLink(config, config.getPort(), clientsConfigs, Message.class);
			NodeService nodeService = nodeServices.get(i);
			services.add(new HDSLedgerService(clientsConfigs, link, config, nodeService));
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
	void HDSLedgerStartsConsensusTest() {
		int n_Nodes = 4;
		int basePortNode = 20000;
		int n_Clients = 2;
		int basePortClient = 30000;
		int basePortHDS = 40000;
		int senderId = 1;
		int seq = 0;
		String cmd = "a";
		Map<Integer, Deque<Slot>> confirmedSlots = genSlotMap(n_Nodes);

		List<ProcessConfig> clientsConfigs = defaultConfigs(n_Clients, basePortClient);
		ProcessConfig[] clientsArray = new ProcessConfig[n_Clients];
		clientsConfigs.toArray(clientsArray);
		List<Link> clientsLinks = defaultLinksClient(n_Clients, clientsConfigs, defaultConfigs(n_Nodes, basePortHDS));


		AppendRequest request = new AppendRequest(senderId, Message.Type.APPEND_REQUEST, cmd, seq);

		List<NodeService> nodeServices = setupNodeServices(n_Nodes, basePortNode);
		List<HDSLedgerService> HDSLedgerServices = setupHDSLedgerServices(n_Nodes, basePortHDS, clientsArray, nodeServices);
		nodeServices.forEach(service -> service.listen());
		HDSLedgerServices.forEach(service -> service.listen());
		nodeServices.forEach(service -> {
			final int id = service.getId();
			Consumer<Slot> observer = s -> confirmedSlots.get(id).add(s);
			service.registerObserver(observer);
		});

		clientsLinks.get(senderId).broadcast(request);

		//nodeServices.forEach(service -> service.startConsensus(nonce, cmd));

		// without assuming stuff about some correctness
		try {
			Thread.sleep(2000);
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
			assertEquals(s.getNonce(), String.format("%s_%s", senderId, seq));
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
                         	