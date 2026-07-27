package dev.csc.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * AES-256-GCM Encryption Helper with Constant-Time Comparison to prevent timing side-channel attacks.
 */
public class CryptoHelper {
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;

    public static String encryptWithKey(String plaintext, SecretKey aesKey) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return "ENC:" + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            LoggerHelper.error("CryptoHelper", "Encryption error: " + e.getMessage());
            return plaintext;
        }
    }

    public static String decryptWithKey(String ciphertextStr, SecretKey aesKey) {
        if (ciphertextStr == null || !ciphertextStr.startsWith("ENC:")) {
            return ciphertextStr;
        }
        try {
            String b64 = ciphertextStr.substring(4);
            byte[] cipherData = Base64.getDecoder().decode(b64);

            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherData);
            byte[] iv = new byte[IV_LENGTH];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LoggerHelper.warn("CryptoHelper", "Decryption failed (wrong key or tampered data)");
            return "§c[Decryption Failed]";
        }
    }

    /** Constant-time byte array comparison against timing side-channel attacks. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return a == b;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
