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
}
