package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.clientLibrary.ClientStub;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.text.MessageFormat;
import java.util.Scanner;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import javafx.util.Pair;

public class Client {

    private static String configPath = "../Service/src/main/resources/";
    // FIXME (dsa)
    //private static String configPath = "/tmp/";

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());

    private static void printUsage() {
        System.out.println("Available commands:");
        System.out.println("     help - Print this message.");
        System.out.println("     transfer <sourcePublicKey> <destinationPublicKey> <amount> - Transfer <amount> from <sourcePublicKey> to <destinationPublicKey> with.");
        System.out.println("     balance <publicKey> - Get the balance of <publicKey>.");
        System.out.println("     exit - Exit the application.");
    }

    public static void main(String[] args) {

        final int clientId = Integer.parseInt(args[0]);

        configPath += "regular_config.json";

        LOGGER.log(Level.INFO, MessageFormat.format("Using clientId = {0}",
                    clientId));

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

        System.out.println("Welcome to HDSLedger Client!");
        printUsage();
        final Scanner scanner = new Scanner(System.in);

        String line;
        String prompt = String.format("[%s @ HDSLedger]$ ", clientId);

        ClientStub stub = new ClientStub(n, config, configs);
        stub.listen();

        while (true) {
            System.out.flush();
            System.out.println();
            System.out.print(prompt);
            line = scanner.nextLine();
            String[] tokens = line.split(" ");
            String command = tokens[0];
            switch (command) {
                case "help":
                    printUsage();
                    break;
                case "transfer":
                    if (tokens.length != 4) {
                        System.out.println("Invalid number of arguments.");
                        printUsage();
                        break;
                    }
                    int source = Integer.parseInt(tokens[1]);
                    int destination = Integer.parseInt(tokens[2]);
                    String sourcePublicKey = configs[source].getPublicKey();
                    String destinationPublicKey = configs[destination].getPublicKey();
                    int amount = Integer.parseInt(tokens[3]);

                    LOGGER.log(Level.INFO, MessageFormat.format("Sending transfer request from {0} to {1} with amount {2}",
                            sourcePublicKey, destinationPublicKey, amount));
                    Optional<Integer> slotOpt = stub.transfer(sourcePublicKey, destinationPublicKey, amount);
                    if (slotOpt.isEmpty()) {
                        System.out.println("Transfer failed.");
                    } else {
                        System.out.println(MessageFormat.format("Transfer request sent. Slot: {0}", slotOpt.get()));
                    }
                    break;
                case "balance":
                    if (tokens.length != 2) {
                        System.out.println("Invalid number of arguments.");
                        printUsage();
                        break;
                    }
                    int client = Integer.parseInt(tokens[1]);
                    String publicKey = configs[client].getPublicKey();
                    LOGGER.log(Level.INFO, MessageFormat.format("Sending check balance request for {0}",
                                client));
                    Optional<Integer> balanceOpt = stub.checkBalance(publicKey);
                    if (balanceOpt.isPresent()) {
                        System.out.println(MessageFormat.format("Balance for {0} is {1}", publicKey, balanceOpt.get()));
                    } else {
                        System.out.println("Balance check failed.");
                    }
                    break;
                case "exit":
                    System.out.println("Exiting...");
                    scanner.close();
                    System.exit(0);
                default:
                    System.out.println("Invalid command.");
                    printUsage();
            }
        }
    }
}
