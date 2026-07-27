package dev.csc.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM End-to-End Encryption (E2EE) helper.
 * Derives strong keys using PBKDF2WithHmacSHA256 with random salt & IV per message.
 */
public class CryptoHelper {
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;

    public static String encrypt(String plaintext, String secretPassword) {
        if (secretPassword == null || secretPassword.isEmpty()) {
            secretPassword = "CSC_DEFAULT_SESSION_SECRET";
        }
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            SecretKey secretKey = deriveKey(secretPassword, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            byteBuffer.put(salt);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return "ENC:" + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            LoggerHelper.error("CryptoHelper", "Encryption error: " + e.getMessage());
            return plaintext; // Fallback to plaintext if error
        }
    }

    public static String decrypt(String ciphertextStr, String secretPassword) {
        if (ciphertextStr == null || !ciphertextStr.startsWith("ENC:")) {
            return ciphertextStr; // Not encrypted or plaintext
        }
        if (secretPassword == null || secretPassword.isEmpty()) {
            secretPassword = "CSC_DEFAULT_SESSION_SECRET";
        }
        try {
            String b64 = ciphertextStr.substring(4);
            byte[] cipherData = Base64.getDecoder().decode(b64);

            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherData);
            byte[] salt = new byte[SALT_LENGTH];
            byteBuffer.get(salt);

            byte[] iv = new byte[IV_LENGTH];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            SecretKey secretKey = deriveKey(secretPassword, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LoggerHelper.warn("CryptoHelper", "Decryption failed (wrong password or tampered data)");
            return "§c[Decryption Failed - Check Password]";
        }
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
