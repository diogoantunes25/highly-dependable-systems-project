package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

import javafx.util.Pair;

public class ProcessConfigBuilder {

    private final ProcessConfig instance = new ProcessConfig();

    // Returns configs for node service and configs for dhs ledger
    // TODO
    public Pair<ProcessConfig[], ProcessConfig[]> fromFile(String path) {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();

            // Gets config with both ports (that will be transformed in two streams with single port)
            ProcessConfig[] ledgerConfig = gson.fromJson(input, ProcessConfig[].class);

            // Config for node service
            List<ProcessConfig> nodesConfig = Arrays.stream(ledgerConfig)
                .filter(config -> config.getPort2().isPresent())
                .map(p -> new ProcessConfig(p.getHostname(), p.getId(), p.getPort2().get(), -1, p.getN(), p.getPublicKey(), p.getPrivateKey()))
                .collect(Collectors.toList());

            // Config for ledger service (can be original one)
            
            return new Pair(
                nodesConfig.toArray(new ProcessConfig[nodesConfig.size()]),
                ledgerConfig
            );

        } catch (FileNotFoundException e) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new HDSSException(ErrorMessage.ConfigFileFormat);
        }
    }

}
