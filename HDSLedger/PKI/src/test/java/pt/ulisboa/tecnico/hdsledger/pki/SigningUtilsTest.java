package pt.ulisboa.tecnico.hdsledger.pki;

import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SigningUtilsTest {

    private static String privKeyPath = "resources/priv.key";
    private static String pubKeyPath = "resources/pub.key";

    @Test
    public void generateAndcheckKeys(@TempDir Path tempDir) throws IOException {
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
            PrivateKey privKey = (PrivateKey) RSAKeyGenerator.read(privKeyPath, "priv");
            PublicKey pubKey = (PublicKey) RSAKeyGenerator.read(pubKeyPath, "pub");

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
            PrivateKey privKey = (PrivateKey) RSAKeyGenerator.read(privKeyPath, "priv");
            PublicKey pubKey = (PublicKey) RSAKeyGenerator.read(pubKeyPath, "pub");

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
    // TODO: add failing test for signature
}
