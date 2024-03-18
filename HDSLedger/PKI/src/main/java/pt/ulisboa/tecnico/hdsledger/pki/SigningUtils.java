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

    public static byte[] encryptWithPublic(Key key, String pathToPublicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException {

        PublicKey publicKey = (PublicKey) RSAKeyGenerator.read(pathToPublicKey, "pub");
        Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encryptCipher.init(Cipher.WRAP_MODE, publicKey);

        return encryptCipher.wrap(key);
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

    public static byte[] decryptWithPrivate(byte[] data, String pathToPrivateKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {

        PrivateKey privateKey = (PrivateKey) RSAKeyGenerator.read(pathToPrivateKey, "priv");
        Cipher decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decryptCipher.init(Cipher.UNWRAP_MODE, privateKey);
        Key sessionKey = decryptCipher.unwrap(data, "AES", Cipher.SECRET_KEY);
        
        return sessionKey.getEncoded();
    }
    public static byte[] generateHMAC(byte[] data, Key key) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            sha256_HMAC.init(key);
            return sha256_HMAC.doFinal(data);
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

        return encrypt(digest.getBytes(), pathToPrivateKey);
    }

    public static boolean verifySignature(String data, String signature, String pathToPublicKey) {
        try {
            String hash = digest(data);
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            String decryptedHash = decrypt(signatureBytes, pathToPublicKey);
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
