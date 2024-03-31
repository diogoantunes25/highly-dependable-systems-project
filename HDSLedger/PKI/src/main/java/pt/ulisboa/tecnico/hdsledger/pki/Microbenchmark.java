package pt.ulisboa.tecnico.hdsledger.pki;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Microbenchamrk for signing utils
*/
public class Microbenchmark {

    public static void generateAndCheckKeys() {
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

    public static void signOnce() {
        String data = "data";
        String privKeyPath = "/tmp/key.priv";

        try {
            // Try to sign
            SigningUtils.sign(data, privKeyPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void verifyOnce(String signature) {
        String data = "data";
        String pubKeyPath = "/tmp/key.pub";

        try {
            // Check signature is good
            if (!SigningUtils.verifySignature(data, signature, pubKeyPath)) {
                throw new RuntimeException("Bad signature where good one was expected");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String args[]) {
        String privKeyPath = "/tmp/key.priv";
        String data = "data";
        generateAndCheckKeys();
        String signature;
        try {
            signature = SigningUtils.sign(data, privKeyPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long signTotalTime = 0;
        long verifyTotalTime = 0;
        int iterations = 30000;

        // Measure the time for signOnce
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            signOnce();
            long endTime = System.nanoTime();
            signTotalTime += endTime - startTime;
        }

        // Measure the time for verifyOnce
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            verifyOnce(signature);
            long endTime = System.nanoTime();
            verifyTotalTime += endTime - startTime;
        }

        // Calculate average time for signOnce and verifyOnce
        double signAverageTime = signTotalTime / (double) iterations;
        double verifyAverageTime = verifyTotalTime / (double) iterations;

        System.out.println("Average time for signOnce: " + signAverageTime + " nanoseconds");
        System.out.println("Average time for verifyOnce: " + verifyAverageTime + " nanoseconds");
    }
}
