package pt.ulisboa.tecnico.hdsledger.pki;

import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.*;

public class SigningUtils {

    public static String encrypt(byte[] data, String pathToPrivateKey)
        throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
        NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PrivateKey privateKey = (PrivateKey) RSAKeyGenerator.read(pathToPrivateKey, "priv");
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, privateKey);
        byte[] encryptedData = encryptCipher.doFinal(data);

        return Base64.getEncoder().encodeToString(encryptedData);
    }

    public static String encryptWithPublic(byte[] data, String pathToPublicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PublicKey publicKey = (PublicKey) RSAKeyGenerator.read(pathToPublicKey, "pub");
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedData = encryptCipher.doFinal(data);

        return Base64.getEncoder().encodeToString(encryptedData);
    }

    public static String decrypt(byte[] data, String pathToPublicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PublicKey publicKey = (PublicKey) RSAKeyGenerator.read(pathToPublicKey, "pub");
        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] decryptedData = decryptCipher.doFinal(data);

        return new String(decryptedData);
    }

    public static String decryptWithPrivate(byte[] data, String pathToPrivateKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PrivateKey privateKey = (PrivateKey) RSAKeyGenerator.read(pathToPrivateKey, "priv");
        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedData = decryptCipher.doFinal(data);

        return new String(decryptedData);
    }
    public static String generateHMAC(String data, Key key) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            sha256_HMAC.init(key);
            byte[] macData = sha256_HMAC.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(macData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        String digestBase64 = encrypt(digest.getBytes(), pathToPrivateKey);

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

    public static Key generateSimKey() {
        try {
            return AESKeyGenerator.generateSimKey();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
