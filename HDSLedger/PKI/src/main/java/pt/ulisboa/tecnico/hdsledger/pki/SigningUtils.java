package pt.ulisboa.tecnico.hdsledger.pki;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
public class SigningUtils {

    public static byte[] encrypt(byte[] data, String pathToPrivateKey)
        throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
        NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PrivateKey privateKey = (PrivateKey) RSAKeyGenerator.read(pathToPrivateKey, "priv");
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, privateKey);
        byte[] encryptedData = encryptCipher.doFinal(data);

        return encryptedData;
    }

    public static byte[] decrypt(byte[] data, String pathToPublicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PublicKey publicKey = (PublicKey) RSAKeyGenerator.read(pathToPublicKey, "pub");
        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] decryptedData = decryptCipher.doFinal(data);

        return decryptedData;
    }

    public static String digest(String data) throws NoSuchAlgorithmException {
        byte[] dataBytes = data.getBytes();
        final String DIGEST_ALGO = "SHA-256";
        MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGO);
        messageDigest.update(dataBytes);
        byte[] digestBytes = messageDigest.digest();

        return Base64.getEncoder().encodeToString(digestBytes);
    }
    public static String sign(String data, String pathToPrivateKey)
            throws NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, IOException {

        String digest = digest(data);
        byte[] digestEncrypted = encrypt(digest.getBytes(), pathToPrivateKey);
        String digestBase64 = Base64.getEncoder().encodeToString(digestEncrypted);

        return digestBase64;
    }

    public static boolean verifySignature(String data, String signature, String pathToPublicKey) {
        try {
            String hash = digest(data);
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            String decryptedHash = new String(decrypt(signatureBytes, pathToPublicKey));
            return hash.equals(decryptedHash);

        } catch (Exception e) {
            return false;
        }
    }
}
