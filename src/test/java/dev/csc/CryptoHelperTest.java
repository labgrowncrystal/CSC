package dev.csc;

import dev.csc.client.CryptoHelper;
import dev.csc.client.ECDHHelper;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import static org.junit.jupiter.api.Assertions.*;

public class CryptoHelperTest {

    @Test
    public void testConstantTimeEquals() {
        assertTrue(CryptoHelper.constantTimeEquals("abc123KeyPinning", "abc123KeyPinning"));
        assertFalse(CryptoHelper.constantTimeEquals("abc123KeyPinning", "wrongKeyPinning"));
        assertFalse(CryptoHelper.constantTimeEquals(null, "wrongKeyPinning"));
    }

    @Test
    public void testEncryptDecryptWithECDHSecret() throws Exception {
        ECDHHelper.ECDHKeyPair clientKeyPair = ECDHHelper.generateKeyPair();
        ECDHHelper.ECDHKeyPair hostKeyPair = ECDHHelper.generateKeyPair();

        SecretKey clientShared = ECDHHelper.deriveSharedSecret(clientKeyPair.privateKey, hostKeyPair.publicKeyBase64);
        SecretKey hostShared = ECDHHelper.deriveSharedSecret(hostKeyPair.privateKey, clientKeyPair.publicKeyBase64);

        String originalMessage = "{\"type\":\"auth\",\"name\":\"Player1\",\"password\":\"secretPass\"}";

        String encrypted = CryptoHelper.encryptWithKey(originalMessage, clientShared);
        assertNotNull(encrypted);
        assertNotEquals(originalMessage, encrypted);

        String decrypted = CryptoHelper.decryptWithKey(encrypted, hostShared);
        assertEquals(originalMessage, decrypted);
    }

    @Test
    public void testDecryptionWithWrongKeyFails() throws Exception {
        ECDHHelper.ECDHKeyPair partyA = ECDHHelper.generateKeyPair();
        ECDHHelper.ECDHKeyPair partyB = ECDHHelper.generateKeyPair();
        ECDHHelper.ECDHKeyPair attacker = ECDHHelper.generateKeyPair();

        SecretKey sharedLegit = ECDHHelper.deriveSharedSecret(partyA.privateKey, partyB.publicKeyBase64);
        SecretKey sharedAttacker = ECDHHelper.deriveSharedSecret(attacker.privateKey, partyB.publicKeyBase64);

        String message = "Secret Message";
        String encrypted = CryptoHelper.encryptWithKey(message, sharedLegit);

        // Attempting decryption with attacker key should fail safely
        String decrypted = CryptoHelper.decryptWithKey(encrypted, sharedAttacker);
        assertNotEquals(message, decrypted, "Decryption with wrong key must not reveal original message!");
        assertTrue(decrypted.contains("Decryption Failed"), "Decryption failure response expected!");
    }

    @Test
    public void testMitMKeyMismatchRejection() throws Exception {
        ECDHHelper.ECDHKeyPair genuineHost = ECDHHelper.generateKeyPair();
        ECDHHelper.ECDHKeyPair mitmAttacker = ECDHHelper.generateKeyPair();

        String expectedPinnedKey = genuineHost.publicKeyBase64;
        String receivedServerKey = mitmAttacker.publicKeyBase64;

        // Pinning verification MUST reject MitM key
        boolean isLegit = CryptoHelper.constantTimeEquals(receivedServerKey, expectedPinnedKey);
        assertFalse(isLegit, "MitM Key substitution MUST be rejected by constantTimeEquals key pinning!");
    }
}
