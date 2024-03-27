package pt.ulisboa.tecnico.hdsledger.client.loader;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.clientLibrary.ClientStub;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.text.MessageFormat;
import java.util.Scanner;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import javafx.util.Pair;

public class LoaderClient {

    private static String configPath = "../Service/src/main/resources/regular_config.json";

    private static final int WARMUP = 20;

    private static final int COOLDOWN = 20;

    private static final CustomLogger LOGGER = new CustomLogger(LoaderClient.class.getName());

    private static void printUsage() {
        System.out.println("LoaderClient <clientId> <txsCount>");
        System.out.println("     txsCount: number of transactions to submit");
    }

    public static void main(String[] args) {

        final int clientId = Integer.parseInt(args[0]);
        final int txCount = Integer.parseInt(args[1]);

        if (txCount <= COOLDOWN + WARMUP) {
            throw new RuntimeException("transaction count should be at least COOLDOWN+WARMUP");
        }

        LOGGER.log(Level.INFO, MessageFormat.format("Using clientId = {0}",
                    clientId));

        LOGGER.log(Level.INFO, MessageFormat.format("Submitting {0} transactions",
                    txCount));

        // Get all configs
        Pair<ProcessConfig[], ProcessConfig[]> bothConfigs = new ProcessConfigBuilder().fromFile(configPath);
        // Client only cares about ledger configs
        ProcessConfig[] configs = bothConfigs.getValue();

        // Find value of n by checking configs with two ports
        int n = (int) Arrays.stream(configs).filter(config -> config.getPort2().isPresent()).count();

        LOGGER.log(Level.INFO, MessageFormat.format("Read {0} configs. There are {1} nodes in the system.",
                    configs.length, n));

        // Get the client config
        Optional<ProcessConfig> clientConfig = Arrays.stream(configs).filter(c -> c.getId() == clientId)
                .findFirst();

        // Ids from 0 to n-1 are reserved for replicas
        if (clientId < n) {
            throw new HDSSException(ErrorMessage.BadClientId);
        }

        ProcessConfig config = clientConfig.get();

        ClientStub stub = new ClientStub(n, config, configs);
        stub.listen();

        int txCompleted = 0;
        int source = clientId;
        int destination = 0;
        int amount = 1;
        long start, end, globalStart = 0, globalEnd = 0;
        double latency, throughput, duration;

        String sourcePublicKey = configs[source].getPublicKey();
        String destinationPublicKey = configs[destination].getPublicKey();

        List<Double> latencies = new ArrayList<>();

        while (txCompleted < txCount) {
            if (txCompleted == WARMUP) {
                globalStart = System.nanoTime();
            }

            LOGGER.log(Level.INFO, MessageFormat.format("Sending transfer request from {0} to {1} with amount {2}",
                    sourcePublicKey, destinationPublicKey, amount));

            start = System.nanoTime();
            Optional<Integer> slotOpt = stub.transfer(sourcePublicKey, destinationPublicKey, amount);
            end = System.nanoTime();

            if (slotOpt.isEmpty()) {
                System.out.println("Transfer failed.");
                throw new RuntimeException("Failed to get transfer through. Make sure you started with sufficient inital credit");
            } else {
                latency = (end - start) / 1_000_000; // nanos to millis
                System.out.println(MessageFormat.format("Transfer request sent. Slot: {0} - took {1} ms", slotOpt.get(), latency));
                if (txCompleted >= WARMUP && txCompleted <= COOLDOWN) {
                    latencies.add(latency);
                }
            }

            txCompleted += 1;

            if (txCompleted == txCount - COOLDOWN) {
                globalEnd = System.nanoTime();
            }
        }

        duration = (globalEnd - globalStart) / 1_000_000_000; // nanos to millis
        double meanLatency = latencies.stream()
                              .mapToDouble(value -> (double) value)
                              .average()
                              .orElse(0);

        throughput = txCompleted / duration;

        System.out.println(MessageFormat.format("Finished load - took {0,number,#.####} seconds", duration));
        System.out.println(MessageFormat.format("Mean Latency: {0,number,#.####} ms", meanLatency));
        System.out.println(MessageFormat.format("Throughput: {0,number,#.####} transactions per second", throughput));
    }
}
