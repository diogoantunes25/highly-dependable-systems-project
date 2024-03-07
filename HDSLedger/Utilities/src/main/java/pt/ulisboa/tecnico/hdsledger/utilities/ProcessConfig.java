package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    public ProcessConfig() {}

    public ProcessConfig(String hostname, int id, int port, int N, String publicKeyPath, String privateKeyPath) {
        this.hostname = hostname;
        this.id = id;
        this.port = port;
        this.N = N;
        this.publicKeyPath = publicKeyPath;
        this.privateKeyPath = privateKeyPath;
    }

    private String hostname;

    private int id;

    private int port;

    private int N;

    private String publicKeyPath;

    private String privateKeyPath;

    public int getPort() {
        return port;
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

    // TODO (dsa): fix getter name
    public String getPublicKey() { return publicKeyPath; }

    public String getPrivateKey() { return privateKeyPath; }
}
