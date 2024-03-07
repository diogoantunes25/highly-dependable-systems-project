package pt.ulisboa.tecnico.hdsledger.pki;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;

public class AESKeyGenerator {
    public static Key generateSimKey() throws GeneralSecurityException {
        // Creating a new instance of SecureRandom class
        SecureRandom securerandom = new SecureRandom();

        // Passing the string to KeyGenerator
        KeyGenerator keygenerator = KeyGenerator.getInstance("AES");
        // Initializing the KeyGenerator with 256 bits.
        keygenerator.init(256, securerandom);
        SecretKey key = keygenerator.generateKey();
        return key;
    }
}
