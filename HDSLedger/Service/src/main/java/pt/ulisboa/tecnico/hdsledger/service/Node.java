package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.HMACLink;
import pt.ulisboa.tecnico.hdsledger.service.services.HDSLedgerService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.util.stream.Collectors;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.List;
import javafx.util.Pair;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    // private static String configPath = "src/main/resources/";
    private static String configPath = "/tmp/regular_config.json";

    public static void main(String[] args) {

        try {
            // Command line arguments
            int id = Integer.valueOf(args[0]);

            // Get all configs
            Pair<ProcessConfig[], ProcessConfig[]> configs = new ProcessConfigBuilder().fromFile(configPath);
            ProcessConfig[] nodesConfigs = configs.getKey();
            ProcessConfig[] ledgerConfigs = configs.getValue();

            // Get my configs
            ProcessConfig ledgerConfig = Arrays.stream(ledgerConfigs).filter(c -> c.getId() == id).findAny().get();
            ProcessConfig nodeConfig = Arrays.stream(nodesConfigs).filter(c -> c.getId() == id).findAny().get();

            // Get client public keys
            List<String> clientPks = Arrays.stream(ledgerConfigs)
                .filter(config -> !config.getPort2().isPresent())
                .map(config -> config.getPublicKey())
                .collect(Collectors.toList());

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node running at {1}:{2};",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort()));

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Ledger running at {1}:{2};",
                    ledgerConfig.getId(), ledgerConfig.getHostname(), ledgerConfig.getPort()));

            // Get a link that has the nodes (for the node service)
            Link nodeLink = new HMACLink(nodeConfig, nodeConfig.getPort(), nodesConfigs,
                    ConsensusMessage.class);

            // Get a link that has all parties in the system
            Link ledgerLink = new HMACLink(ledgerConfig, ledgerConfig.getPort(), ledgerConfigs,
                    AppendMessage.class);

            NodeService nodeService = new NodeService(nodeLink, nodeConfig, nodesConfigs, clientPks);
            HDSLedgerService hdsLedgerService = new HDSLedgerService(ledgerConfigs, ledgerLink, ledgerConfig, nodeService);
            
            nodeService.listen();

            hdsLedgerService.listen();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
