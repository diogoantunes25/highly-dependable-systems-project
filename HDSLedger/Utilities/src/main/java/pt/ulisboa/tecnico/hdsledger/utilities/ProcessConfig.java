package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.Optional;

public class ProcessConfig {
    public ProcessConfig() {}

    public ProcessConfig(String hostname, int id, int port, int port2, int N, String publicKeyPath, String privateKeyPath) {
        this.hostname = hostname;
        this.id = id;
        this.port = port;
        this.port2 = port2;
        this.N = N;
        this.publicKeyPath = publicKeyPath;
        this.privateKeyPath = privateKeyPath;
    }

    private String hostname;

    private int id;

    private int port;

    // Port for node service (empty for clients)
    private int port2;

    private int N;

    private String publicKeyPath;

    private String privateKeyPath;

    public int getPort() {
        return port;
    }

    public Optional<Integer> getPort2() {
        if (port2 > 0) {
            return Optional.of(port2);
        }

        return Optional.empty();
    }

    public int getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public int getN() {
        return N;
    }

    public String getPublicKey() { return publicKeyPath; }

    public String getPrivateKey() { return privateKeyPath; }
}
