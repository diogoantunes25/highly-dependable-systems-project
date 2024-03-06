package pt.ulisboa.tecnico.hdsledger.communication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.nio.file.Path;

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
        ProcessConfig processConfig1 = new ProcessConfig(true, "localhost", 1, 8080, 4, pubKeyPath1, privKeyPath1);
        ProcessConfig processConfig2 = new ProcessConfig(false, "localhost", 2, 8081, 4, pubKeyPath2, privKeyPath2);

        ProcessConfig[] processConfigs = {processConfig1, processConfig2};
        Class<? extends Message> messageClass = Message.class;
        // create APLink
        APLink apLink1 = new APLink(processConfig1, 5001, processConfigs, messageClass, true, 1000);
        APLink apLink2 = new APLink(processConfig2, 5002, processConfigs, messageClass, true, 1000);

        // create HMACLink
        HMACLink hmacLink1 = new HMACLink(processConfig1, processConfigs, apLink1);
        HMACLink hmacLink2 = new HMACLink(processConfig2, processConfigs, apLink2);

        // create a message
        Message message = new Message(processConfig1.getId(), Message.Type.APPEND);
        hmacLink1.send(2, message);
        System.out.println("Message with id: " + message.getMessageId() + " sent to " + message.getReceiver() +
                            " with type: " + message.getType() + " and sender: " + message.getSenderId() +
                            " and Hmac: " + ((HMACMessage) message).getHmac());
        try {
            while (hmacLink2.receive() == null) {
                Thread.sleep(100);
            }
            Message received = hmacLink2.receive();
            assert(received.getSenderId() == 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
