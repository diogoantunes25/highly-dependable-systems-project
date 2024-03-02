package pt.ulisboa.tecnico.hdsledger.client;

import java.util.Scanner;

public class Client {

    private static void printUsage() {
        System.out.println("Available commands:");
        System.out.println("     append <string> - Append a string to the ledger.");
        System.out.println("     exit - Exit the application.");
    }

    public static void main(String[] args) {
        System.out.println("Welcome to HDSLedger Client!");
        printUsage();
        final Scanner scanner = new Scanner(System.in);

        String clientId = "19"; // Hardcoded for now, then read from config
        String line = "";
        String prompt = String.format("[%s @ HDSLedger]$ ", clientId);

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