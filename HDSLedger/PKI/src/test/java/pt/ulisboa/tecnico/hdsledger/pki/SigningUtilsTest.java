package pt.ulisboa.tecnico.hdsledger.pki;

import java.nio.file.Path;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SigningUtilsTest {

    @Test
    public void generateAndCheckKeys() throws IOException {
        String pubKeyPath = "/tmp/key.pub";
        String privKeyPath = "/tmp/key.priv";

        try {
            RSAKeyGenerator.write(privKeyPath, pubKeyPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            PrivateKey privKey = (PrivateKey) RSAKeyGenerator.read(privKeyPath, "priv");
            PublicKey pubKey = (PublicKey) RSAKeyGenerator.read(pubKeyPath, "pub");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void verifyGoodSignature() throws IOException, GeneralSecurityException {
        String data = "data";

        String pubKeyPath = "/tmp/key.pub";
        String privKeyPath = "/tmp/key.priv";

        try {
            RSAKeyGenerator.read(pubKeyPath, "pub");
            RSAKeyGenerator.read(privKeyPath, "priv");
        } catch (Exception e) {
            RSAKeyGenerator.write(privKeyPath, pubKeyPath);
        }

        try {
            // Try to sign
            String signature = SigningUtils.sign(data, privKeyPath);

            // Check signature is good
            if (!SigningUtils.verifySignature(data, signature, pubKeyPath)) {
                throw new RuntimeException("Bad signature where good one was expected");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void verifyBadSignature() throws IOException, GeneralSecurityException {
        String data = "data";
        String otherData = "date";

        String pubKeyPath = "/tmp/key.pub";
        String privKeyPath = "/tmp/key.priv";

        try {
            RSAKeyGenerator.read(pubKeyPath, "pub");
            RSAKeyGenerator.read(privKeyPath, "priv");
        } catch (Exception e) {
            RSAKeyGenerator.write(privKeyPath, pubKeyPath);
        }

        try {
            // Try to sign
            String signature = SigningUtils.sign(data, privKeyPath);
            // Check signature is bad
            if (SigningUtils.verifySignature(otherData, signature, pubKeyPath)) {
                throw new RuntimeException("Bad signature where good one was expected");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void verifyWrongSignature() throws IOException, GeneralSecurityException {
        String data = "data";

        String pubKeyPath1 = "/tmp/key1.pub";
        String privKeyPath1 = "/tmp/key1.priv";

        try {
            RSAKeyGenerator.read(pubKeyPath1, "pub");
            RSAKeyGenerator.read(privKeyPath1, "priv");
        } catch (Exception e) {
            RSAKeyGenerator.write(privKeyPath1, pubKeyPath1);
        }

        String pubKeyPath2 = "/tmp/key2.pub";
        String privKeyPath2 = "/tmp/key2.priv";

        try {
            RSAKeyGenerator.read(pubKeyPath2, "pub");
            RSAKeyGenerator.read(privKeyPath2, "priv");
        } catch (Exception e) {
            RSAKeyGenerator.write(privKeyPath2, pubKeyPath2);
        }

        try {
            // Try to sign
            String signature = SigningUtils.sign(data, privKeyPath1);

            // Check signature is bad
            if (SigningUtils.verifySignature(data, signature, pubKeyPath2)) {
                throw new RuntimeException("Good signature where bad one was expected");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHMAC() {
        String data = "data";
        String otherData = "date";
        try {
            Key key = AESKeyGenerator.generateSimKey();
            byte[] mac = SigningUtils.generateHMAC(data.getBytes(), key);

            if (Arrays.equals(mac, SigningUtils.generateHMAC(otherData.getBytes(), key))) {
                throw new RuntimeException("Good MAC where bad one was expected");
            }

            // test that the MAC is valid
            if (!Arrays.equals(mac, SigningUtils.generateHMAC(data.getBytes(), key))) {
                throw new RuntimeException("Bad MAC where good one was expected");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
