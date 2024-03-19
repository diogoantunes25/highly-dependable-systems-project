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
        System.out.println("     check_balance <publicKey> - Get the balance of <publicKey>.");
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
                    String sourcePublicKey = tokens[1];
                    String destinationPublicKey = tokens[2];
                    int amount = Integer.parseInt(tokens[3]);

                    LOGGER.log(Level.INFO, MessageFormat.format("Sending transfer request from {0} to {1} with amount {2}",
                            sourcePublicKey, destinationPublicKey, amount));
                    int slot = stub.transfer(sourcePublicKey, destinationPublicKey, amount);
                    System.out.println(MessageFormat.format("Transfer request sent. Slot: {0}", slot));
                    break;
                case "balance":
                    if (tokens.length != 2) {
                        System.out.println("Invalid number of arguments.");
                        printUsage();
                        break;
                    }
                    String publicKey = tokens[1];
                    LOGGER.log(Level.INFO, MessageFormat.format("Sending check balance request for {0}",
                                publicKey));
                    int balance = stub.checkBalance(publicKey);
                    System.out.println(MessageFormat.format("Balance for {0} is {1}", publicKey, balance));
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
