package pt.ulisboa.tecnico.hdsledger.communication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.ulisboa.tecnico.hdsledger.consensus.message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;

public class HMACLinkTest {
    @Test
    public void testHMACLink(@TempDir Path tempDir) {
        // generate nodes private and public keys
        String privKeyPath1 = tempDir.resolve("pub1.key").toString();
        String pubKeyPath1 = tempDir.resolve("priv1.key").toString();
        String privKeyPath2 = tempDir.resolve("pub2.key").toString();
        String pubKeyPath2 = tempDir.resolve("priv2.key").toString();
        try {
            RSAKeyGenerator.write(privKeyPath1, pubKeyPath1);
            RSAKeyGenerator.write(privKeyPath2, pubKeyPath2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // create a process configuration
        ProcessConfig processConfig1 = new ProcessConfig("localhost", 1, 8080, -1, 4, pubKeyPath1, privKeyPath1);
        ProcessConfig processConfig2 = new ProcessConfig( "localhost", 2, 8081, -1, 4, pubKeyPath2, privKeyPath2);

        ProcessConfig[] processConfigs = {processConfig1, processConfig2};
        Class<? extends Message> messageClass = ConsensusMessage.class;

        // create HMACLink
        HMACLink hmacLink1 = new HMACLink(processConfig1, 8080, processConfigs, messageClass, true, 1000);
        HMACLink hmacLink2 = new HMACLink(processConfig2, 8081, processConfigs, messageClass, true, 1000);

        listen(hmacLink1);
        listen(hmacLink2);

        // create a message
        Message message = new Message(processConfig1.getId(), Message.Type.PREPARE);
        hmacLink1.send(processConfig2.getId(), message);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void listen(Link link) {
        new Thread(() -> {
            try {
                while (true) {
                    Message message = link.receive();
                    System.out.println(MessageFormat.format(
                                "Received message from {0} of type {1} with hmac",
                                message.getSenderId(),
                                message.getType()));
                }
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
