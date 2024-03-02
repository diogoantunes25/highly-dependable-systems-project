package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    public ProcessConfig() {}

    // dsa: not sure why this is part of the process configuration
    private boolean isLeader;

    private String hostname;

    private int id;

    private int port;

    private int N;

    public boolean isLeader() {
        return isLeader;
    }

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
}
