package pt.ulisboa.tecnico.hdsledger.clientLibrary;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.*;

import pt.ulisboa.tecnico.hdsledger.consensus.message.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.PrepareMessage;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientStub {
    // Client identifier (self)
    private final ProcessConfig config;
    // Link to communicate with nodes
    private final Link link;
    // Map of responses from nodes
    private final Map<Integer, AppendRequest> responses = new HashMap<>(); // TODO - Change AppendRequest to appropriate type of Response
    // Known blockchain
    private final List<String> blockchain = new ArrayList<>();
    // Current request ID
    private AtomicInteger requestId = new AtomicInteger(0);

    public ClientStub(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs,
                   boolean activateLogs) throws HDSSException {
        this.config = clientConfig;

        // Create link to communicate with nodes
        this.link = new APLink(clientConfig, clientConfig.getPort(), nodeConfigs, ConsensusMessage.class, activateLogs, 5000);
        // TODO - ConsensusMessage is not correct i think
    }

    public void printBlockchain(List<String> blockchain) {
        System.out.println("Blockchain:");
        blockchain.forEach(System.out::println);
    }

    public List<String> append(String value) {

        int currentRequestId = this.requestId.getAndIncrement(); // nonce
        AppendRequest request = new AppendRequest(config.getId(), Message.Type.APPEND, value, currentRequestId);

        this.link.broadcast(request);
        // response and deal with response
        // Response response
        //while (response = responses.get(currentRequestId) == null) {
            // wait - how??
        //}
        // Add new values to the blockchain with the response

        List<String> blockchainValues = new ArrayList<>(); // response.getValues() ?
        blockchainValues.addAll(blockchainValues);
        return blockchainValues;
    }

    public void listen() {
        try {
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        // Separate thread to handle each message
                        switch (message.getType()) {
                            case REPLY -> {
                                // add to logger?

                                // Add new values to the blockchain
                                // Response response = smth -> cast message to appropriate type?
                                // responses.put(response.getRequestId(), response);
                            }
                            case ACK -> {
                                continue; // maybe add to logger?
                            }
                            default -> {
                                throw new HDSSException(ErrorMessage.CannotParseMessage);
                            }
                        }
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
