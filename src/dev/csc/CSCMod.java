package dev.csc;

import net.fabricmc.api.ModInitializer;

public class CSCMod implements ModInitializer {
    public static final String MOD_ID = "csc";
    public static final int DEFAULT_PORT = 49156;

    @Override
    public void onInitialize() {
        System.out.println("[CSC] Clientside Chat v1.4.1 (Public Key Pinning & ECDH) loaded.");
    }
}
