package pt.ulisboa.tecnico.hdsledger.pki;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Level;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RSAKeyGenerator {

    private static final CustomLogger LOGGER = new CustomLogger(RSAKeyGenerator.class.getName());

    private static Map<String, Key> memoizedKeys = new ConcurrentHashMap<>();

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
                LOGGER.log(Level.INFO, "Reading keys");
                read(privkeyPath, "priv");
                read(pubkeyPath, "pub");
                break;
            case "w":
                LOGGER.log(Level.INFO, "Writing keys");
                write(privkeyPath, pubkeyPath);
                break;
            default:
                throw new IllegalArgumentException("Invalid mode. Usage: [r|w] <path-to-priv-key-id>.priv <path-to-pub-key-id>.pub");
        }
    }

    public static void write(String privKeyPath, String pubKeyPath) throws GeneralSecurityException, IOException {
        // get an RSA private key
        LOGGER.log(Level.INFO, "Generating RSA keys");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(4096);
        KeyPair keys = keyGen.generateKeyPair();
        LOGGER.log(Level.INFO, "Keys generated");

        LOGGER.log(Level.INFO, "Private Key:");
        PrivateKey privKey = keys.getPrivate();
        byte[] privKeyEncoded = privKey.getEncoded();
        LOGGER.log(Level.INFO, "Encoded type '" + privKey.getFormat() + "' ..." );

        LOGGER.log(Level.INFO, DataUtils.bytesToHex(privKeyEncoded));

        LOGGER.log(Level.INFO, "Public Key:");
        PublicKey pubKey = keys.getPublic();
        byte[] pubKeyEncoded = pubKey.getEncoded();
        LOGGER.log(Level.INFO, "Encoded type '" + pubKey.getFormat() + "' ..." );

        LOGGER.log(Level.INFO, DataUtils.bytesToHex(pubKeyEncoded));

        LOGGER.log(Level.INFO, "Writing Private key to '" + privKeyPath + "' ..." );
        try (FileOutputStream privFos = new FileOutputStream(privKeyPath)) {
            privFos.write(privKeyEncoded);
        }
        LOGGER.log(Level.INFO, "Writing Public key to '" + pubKeyPath + "' ..." );
        try (FileOutputStream pubFos = new FileOutputStream(pubKeyPath)) {
            pubFos.write(pubKeyEncoded);
        }
    }

    public static Key read(String keyPath, String type) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        if (memoizedKeys.containsKey(keyPath)) {
            return memoizedKeys.get(keyPath);
        }

        LOGGER.log(Level.INFO, "Reading key from '" + keyPath + "' ..." );
        byte[] encoded;
        try (FileInputStream fis = new FileInputStream(keyPath)) {
            encoded = new byte[fis.available()];
            fis.read(encoded);
        }
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        if (type.equals("pub") ){
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            Key key = keyFactory.generatePublic(keySpec);
            memoizedKeys.put(keyPath, key);
            return key;
        }

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        Key key = keyFactory.generatePrivate(keySpec);
        memoizedKeys.put(keyPath, key);

        return key;
    }
}
