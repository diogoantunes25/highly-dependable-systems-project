package pt.ulisboa.tecnico.hdsledger.pki;

import javax.crypto.KeyGenerator;
import java.security.GeneralSecurityException;
import java.security.Key;

public class AESKeyGenerator {
    public static Key generateSimKey() throws GeneralSecurityException {
        // get an AES private key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        Key key = keyGen.generateKey();
        return key;
    }
}
