package pt.ulisboa.tecnico.hdsledger.pki;

import org.junit.jupiter.api.Test;

public class SigningUtilsTest {

    private String privKeyPath = "src/test/java/resources/priv.key";
    private String pubKeyPath = "src/test/java/resources/pub.key";

    @Test
    public void generateAndcheckKeys() {
        try {
            RSAKeyGenerator.write(privKeyPath, pubKeyPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            RSAKeyGenerator.read(privKeyPath, "priv");
            RSAKeyGenerator.read(pubKeyPath, "pub");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /*

    @Test
    public void testSign() {
        try {
            String data = "data";
            String signature = SigningUtils.sign(data, privKeyPath);
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    @Test
    public void verifySignature() {
        try {
            String data = "data";
            String signature = SigningUtils.sign(data, privKeyPath);
            boolean verified = SigningUtils.verifySignature(data, signature, pubKeyPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/
}