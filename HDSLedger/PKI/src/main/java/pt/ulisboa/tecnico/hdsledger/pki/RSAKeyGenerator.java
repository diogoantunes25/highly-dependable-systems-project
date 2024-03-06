package pt.ulisboa.tecnico.hdsledger.pki;

import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.nio.file.Path;
import java.io.File;

public class RSAKeyGenerator {

    public static void main(String[] args) throws Exception {

        // check args
        if (args.length != 3) {
            System.err.println("Usage: [r|w] <path-to-priv-key-id>.priv <path-to-pub-key-id>.pub");
            return;
        }

        final String mode = args[0];
        final String privkeyPath = args[1];
        final String pubkeyPath = args[2];

        switch (mode.toLowerCase()) {
            case "r":
                System.out.println("Load keys");
                read(privkeyPath, "priv");
                read(pubkeyPath, "pub");
                break;
            case "w":
                System.out.println("Generate and save keys");
                write(privkeyPath, pubkeyPath);
                break;
            default:
                throw new IllegalArgumentException("Invalid mode. Usage: [r|w] <path-to-priv-key-id>.priv <path-to-pub-key-id>.pub");
        }

        System.out.println("Done.");
    }

    public static void write(String privKeyPath, String pubKeyPath) throws GeneralSecurityException, IOException {
        // get an RSA private key
        System.out.println("Generating RSA key ..." );
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(4096);
        KeyPair keys = keyGen.generateKeyPair();
        System.out.println("Finish generating RSA keys");

        System.out.println("Private Key:");
        PrivateKey privKey = keys.getPrivate();
        byte[] privKeyEncoded = privKey.getEncoded();
        System.out.println("Encoded type '" + privKey.getFormat() + "' ..." );

        System.out.println(DataUtils.bytesToHex(privKeyEncoded));
        System.out.println("Public Key:");
        PublicKey pubKey = keys.getPublic();
        byte[] pubKeyEncoded = pubKey.getEncoded();
        System.out.println("Encoded type '" + pubKey.getFormat() + "' ..." );

        System.out.println(DataUtils.bytesToHex(pubKeyEncoded));

        System.out.println("Writing Private key to '" + privKeyPath + "' ..." );
        try (FileOutputStream privFos = new FileOutputStream(privKeyPath)) {
            privFos.write(privKeyEncoded);
        }
        System.out.println("Writing Public key to '" + pubKeyPath + "' ..." );
        try (FileOutputStream pubFos = new FileOutputStream(pubKeyPath)) {
            pubFos.write(pubKeyEncoded);
        }
    }

    public static Key read(String keyPath, String type) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        System.out.println("Reading key from file " + keyPath + " ...");
        byte[] encoded;
        try (FileInputStream fis = new FileInputStream(keyPath)) {
            encoded = new byte[fis.available()];
            fis.read(encoded);
        }
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        if (type.equals("pub") ){
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return keyFactory.generatePublic(keySpec);
        }

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return keyFactory.generatePrivate(keySpec);
    }
}
