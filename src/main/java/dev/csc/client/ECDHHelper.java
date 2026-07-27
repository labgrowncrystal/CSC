package dev.csc.client;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Elliptic Curve Diffie-Hellman (ECDH) Key Exchange Helper.
 * Establishes a shared 256-bit secret between client and server over an untrusted network
 * without transmitting the secret over the wire or storing it in tokens.
 */
public class ECDHHelper {
    public static class ECDHKeyPair {
        public PrivateKey privateKey;
        public PublicKey publicKey;
        public String publicKeyBase64;

        public ECDHKeyPair(PrivateKey privateKey, PublicKey publicKey, String publicKeyBase64) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.publicKeyBase64 = publicKeyBase64;
        }
    }

    public static ECDHKeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String pubB64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return new ECDHKeyPair(keyPair.getPrivate(), keyPair.getPublic(), pubB64);
    }

    public static SecretKey deriveSharedSecret(PrivateKey myPrivateKey, String peerPublicKeyBase64) throws Exception {
        byte[] peerPubKeyBytes = Base64.getDecoder().decode(peerPublicKeyBase64);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey peerPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(peerPubKeyBytes));

        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(myPrivateKey);
        keyAgreement.doPhase(peerPublicKey, true);

        byte[] sharedSecretBytes = keyAgreement.generateSecret();
        
        // Hash shared secret with SHA-256 to produce a uniform 256-bit AES key
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] aesKeyBytes = md.digest(sharedSecretBytes);

        return new SecretKeySpec(aesKeyBytes, "AES");
    }
}
