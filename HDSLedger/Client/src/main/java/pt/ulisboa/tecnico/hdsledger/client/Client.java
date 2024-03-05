package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.clientLibrary.ClientStub;

import java.util.Scanner;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Client {

    private static String nodesConfigPath = "../Service/src/main/resources/";
    private static String clientsConfigPath = "src/main/resources/";

    private static void printUsage() {
        System.out.println("Available commands:");
        System.out.println("     append <string> - Append a string to the ledger.");
        System.out.println("     exit - Exit the application.");
    }

    public static void main(String[] args) {

        final int clientId = Integer.parseInt(args[0]);
        nodesConfigPath += args[1];
        clientsConfigPath += args[2];
        boolean showDebugLogs = false;
        if (args.length == 4) {
            showDebugLogs = args[3].equals("-debug");
        }

        // Get all configs
        ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
        ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);

        // Get the client config
        Optional<ProcessConfig> clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId() == clientId)
                .findFirst();
        if (clientConfig.isEmpty()) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        }
        ProcessConfig config = clientConfig.get();

        System.out.println("Welcome to HDSLedger Client!");
        printUsage();
        final Scanner scanner = new Scanner(System.in);

        String line = "";
        String prompt = String.format("[%s @ HDSLedger]$ ", clientId);

        ClientStub stub = new ClientStub(config, nodeConfigs, clientConfigs, showDebugLogs);
        stub.listen();

        while (true) {
            System.out.flush();
            System.out.println();
            System.out.print(prompt);
            line = scanner.nextLine();
            String[] tokens = line.split(" ");
            String command = tokens[0];
            switch (command) {
                case "append":
                    String string = tokens[1];
                    System.out.println("Appending <" + string + ">...");
                    try {
                        int slotId = stub.append(string);
                        System.out.println("Appended <" + string + ">..." + " Slot ID: " + slotId);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
