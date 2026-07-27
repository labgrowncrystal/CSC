package dev.csc;

import dev.csc.client.ECDHHelper;
import dev.csc.client.TokenHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TokenHelperTest {

    @Test
    public void testTokenGenerationAndParsing() throws Exception {
        ECDHHelper.ECDHKeyPair hostKeyPair = ECDHHelper.generateKeyPair();

        String token = TokenHelper.generateToken("93.184.216.34", "192.168.1.100", 49156, 24, 5, hostKeyPair.publicKeyBase64);

        assertTrue(token.startsWith("CSC-"));

        TokenHelper.SessionTokenData parsed = TokenHelper.parseToken(token);

        assertEquals("93.184.216.34", parsed.publicIp);
        assertEquals("192.168.1.100", parsed.lanIp);
        assertEquals(49156, parsed.port);
        assertEquals(5, parsed.maxClients);
        assertEquals(hostKeyPair.publicKeyBase64, parsed.hostPubKey);
    }

    @Test
    public void testTamperedTokenRejection() throws Exception {
        ECDHHelper.ECDHKeyPair hostKeyPair = ECDHHelper.generateKeyPair();
        String token = TokenHelper.generateToken("93.184.216.34", "192.168.1.100", 49156, 24, 5, hostKeyPair.publicKeyBase64);

        // Tamper token payload string
        String tamperedToken = token.substring(0, token.length() - 6) + "XXXXXX";

        assertThrows(Exception.class, () -> {
            TokenHelper.parseToken(tamperedToken);
        }, "Tampered or corrupted session token MUST throw exception!");
    }

    @Test
    public void testExpiredTokenRejection() throws Exception {
        ECDHHelper.ECDHKeyPair hostKeyPair = ECDHHelper.generateKeyPair();

        // Generate token expired -1 hour ago
        String expiredToken = TokenHelper.generateToken("93.184.216.34", "192.168.1.100", 49156, -1, 5, hostKeyPair.publicKeyBase64);

        assertThrows(IllegalStateException.class, () -> {
            TokenHelper.parseToken(expiredToken);
        }, "Expired session token MUST throw IllegalStateException!");
    }
}
