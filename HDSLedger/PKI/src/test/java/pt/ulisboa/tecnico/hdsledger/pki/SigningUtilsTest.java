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
    public void generateAndCheckKeys(@TempDir Path tempDir) throws IOException {
        String pubKeyPath = tempDir.resolve("pub.key").toString();
        String privKeyPath = tempDir.resolve("priv.key").toString();

        System.out.printf("pub.key = %s\n", pubKeyPath);
        System.out.printf("priv.key = %s\n", privKeyPath);

        try {

            RSAKeyGenerator.write(privKeyPath, pubKeyPath);
            PrivateKey privKey = (PrivateKey) RSAKeyGenerator.read(privKeyPath, "priv");
            PublicKey pubKey = (PublicKey) RSAKeyGenerator.read(pubKeyPath, "pub");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void verifyGoodSignature(@TempDir Path tempDir) throws IOException {
        String data = "data";

        String pubKeyPath = tempDir.resolve("pub.key").toString();
        String privKeyPath = tempDir.resolve("priv.key").toString();

        System.out.printf("pub.key = %s\n", pubKeyPath);
        System.out.printf("priv.key = %s\n", privKeyPath);

        try {

            // Generate key pair
            RSAKeyGenerator.write(privKeyPath, pubKeyPath);

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
    public void verifyBadSignature(@TempDir Path tempDir) throws IOException {
        String data = "data";
        String otherData = "date";

        String pubKeyPath = tempDir.resolve("pub.key").toString();
        String privKeyPath = tempDir.resolve("priv.key").toString();

        System.out.printf("pub.key = %s\n", pubKeyPath);
        System.out.printf("priv.key = %s\n", privKeyPath);

        try {

            // Generate key pair
            RSAKeyGenerator.write(privKeyPath, pubKeyPath);

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
    public void verifyWrongSignature(@TempDir Path tempDir) throws IOException {
        String data = "data";

        String pubKeyPath1 = tempDir.resolve("pub1.key").toString();
        String privKeyPath1 = tempDir.resolve("priv1.key").toString();

        String pubKeyPath2 = tempDir.resolve("pub2.key").toString();
        String privKeyPath2 = tempDir.resolve("priv2.key").toString();

        System.out.printf("pub1.key = %s\n", pubKeyPath1);
        System.out.printf("priv1.key = %s\n", privKeyPath1);

        System.out.printf("pub2.key = %s\n", pubKeyPath2);
        System.out.printf("priv2.key = %s\n", privKeyPath2);

        try {

            // Generate key pair
            RSAKeyGenerator.write(privKeyPath1, pubKeyPath1);

            RSAKeyGenerator.write(privKeyPath2, pubKeyPath2);

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
    public void testHMAC(@TempDir Path tempDir) throws IOException, GeneralSecurityException {
        String data = "data";
        String otherData = "date";

        String keyPath = tempDir.resolve("key.key").toString();

        System.out.printf("key.key = %s\n", keyPath);

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
