package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.APLink;
import pt.ulisboa.tecnico.hdsledger.service.services.HDSLedgerService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String configPath = "src/main/resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            int id = Integer.valueOf(args[0]);
            String nodesConfigPath = configPath + args[1];
            String clientsConfigPath = configPath + args[2];

            // Create configuration instances
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
            ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId() == id).findAny().get();

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; is leader: {3}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                    nodeConfig.isLeader()));

            // Abstraction to send and receive messages
            Link linkToNodes = new APLink(nodeConfig, nodeConfig.getPort(), nodeConfigs,
                    ConsensusMessage.class);

            Link linkToClients = new APLink(nodeConfig, nodeConfig.getPort(), clientConfigs,
                    ConsensusMessage.class);

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(linkToNodes, nodeConfig, nodeConfigs);

            HDSLedgerService hdsLedgerService = new HDSLedgerService(clientConfigs, linkToClients, nodeConfig, nodeService);
            
            nodeService.listen();

            hdsLedgerService.listen();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
