package pt.ulisboa.tecnico.hdsledger.communication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;

public class HMACLinkTest {
    @Test
    public void testHMACLink() throws GeneralSecurityException, IOException {
        // generate nodes private and public keys
        String privKeyPath1 = "/tmp/node1.priv";
        String pubKeyPath1 = "/tmp/node1.pub";
        String privKeyPath2 = "/tmp/node2.priv";
        String pubKeyPath2 = "/tmp/node2.pub";
        try {
            RSAKeyGenerator.read(privKeyPath1, "priv");
            RSAKeyGenerator.read(pubKeyPath1, "pub");
        } catch (Exception e) {
            RSAKeyGenerator.write(privKeyPath1, pubKeyPath1);
        }

        try {
            RSAKeyGenerator.read(privKeyPath2, "priv");
            RSAKeyGenerator.read(pubKeyPath2, "pub");
        } catch (Exception e) {
            RSAKeyGenerator.write(privKeyPath2, pubKeyPath2);
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
