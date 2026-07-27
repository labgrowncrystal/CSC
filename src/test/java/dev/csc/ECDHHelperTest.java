package dev.csc;

import dev.csc.client.ECDHHelper;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import static org.junit.jupiter.api.Assertions.*;

public class ECDHHelperTest {

    @Test
    public void testKeyPairGenerationAndDerivation() throws Exception {
        ECDHHelper.ECDHKeyPair partyA = ECDHHelper.generateKeyPair();
        ECDHHelper.ECDHKeyPair partyB = ECDHHelper.generateKeyPair();

        assertNotNull(partyA.publicKeyBase64);
        assertNotNull(partyB.publicKeyBase64);

        SecretKey secretA = ECDHHelper.deriveSharedSecret(partyA.privateKey, partyB.publicKeyBase64);
        SecretKey secretB = ECDHHelper.deriveSharedSecret(partyB.privateKey, partyA.publicKeyBase64);

        assertArrayEquals(secretA.getEncoded(), secretB.getEncoded());
    }
}
