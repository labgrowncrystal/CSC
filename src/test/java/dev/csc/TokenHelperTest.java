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
}
