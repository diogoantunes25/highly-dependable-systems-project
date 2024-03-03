package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.consensus.message.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.consensus.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class HDSLedgerService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    
    // Clients configurations
    private final ProcessConfig[] clientsConfig;

    // Current node config
    private final ProcessConfig config;

    // Link to communicate with nodes
    private final Link link;

    // Node service that allows start consensus instances
    private final NodeService nodeService;

    // Request queue
    private Queue<AppendRequest> requests = new LinkedList<>();

    public HDSLedgerService(ProcessConfig[] clientConfigs, Link link, ProcessConfig config, NodeService nodeService) {
        this.clientsConfig = clientConfigs;
        this.link = link;   
        this.config = config;
        this.nodeService = nodeService;
    }

    public void append(AppendRequest request) {
        // Add request to queue
        requests.add(request);
    }


    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {

                                case APPEND ->
                                    append((AppendRequest) message);

                                default ->
                                    LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received unknown message from {1}",
                                            config.getId(), message.getSenderId()));  

                            }

                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

