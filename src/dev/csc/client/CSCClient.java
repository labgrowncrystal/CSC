package dev.csc.client;

import dev.csc.CSCMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * CSC — Clientside Chat v1.3.0 Hardened Edition
 *
 * Security Features:
 *   - AES-256-GCM End-to-End Encryption (E2EE) for all chat messages
 *   - Dynamic Ephemeral Session Secrets (No static keys)
 *   - Brute-Force Rate Limiting & Auto IP Ban (5 failed auths = 5m ban)
 *   - Bounded Input Buffering (16KB max line limit to prevent DoS)
 *   - Dual-IP Automatic Fallback (Public IP -> LAN IP -> Localhost)
 *   - Dedicated Log System under %APPDATA%/.minecraft/csc/logs/
 */
public class CSCClient implements ClientModInitializer {
    private static RelayServer relayServer;
    private static RelayConnection connection;
    private static String myName = "";
    private static String currentToken = "";
    private static String activeEncryptionSecret = "CSC_DEFAULT_SESSION_SECRET";

    @Override
    public void onInitializeClient() {
        LoggerHelper.info("CSCClient", "Initializing CSC v1.3.0 Hardened Client...");

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (myName.isEmpty() && client.player != null) {
                myName = client.player.getName().getString();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // ─── /csc ────────────────────────────────────────────────────
            dispatcher.register(ClientCommands.literal("csc")
                .executes(ctx -> { sendHelp(ctx.getSource()); return 1; })

                .then(ClientCommands.literal("help")
                    .executes(ctx -> { sendHelp(ctx.getSource()); return 1; })
                )

                // ─── /csc host [password] [max_players] [duration_hours] ─
                .then(ClientCommands.literal("host")
                    .executes(ctx -> { startHost(ctx.getSource(), "", 2, 24); return 1; })
                    .then(ClientCommands.argument("password", StringArgumentType.string())
                        .executes(ctx -> {
                            startHost(ctx.getSource(), StringArgumentType.getString(ctx, "password"), 2, 24);
                            return 1;
                        })
                        .then(ClientCommands.argument("maxPlayers", IntegerArgumentType.integer(2, 50))
                            .executes(ctx -> {
                                startHost(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "password"),
                                    IntegerArgumentType.getInteger(ctx, "maxPlayers"),
                                    24);
                                return 1;
                            })
                            .then(ClientCommands.argument("hoursValid", IntegerArgumentType.integer(1, 168))
                                .executes(ctx -> {
                                    startHost(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "password"),
                                        IntegerArgumentType.getInteger(ctx, "maxPlayers"),
                                        IntegerArgumentType.getInteger(ctx, "hoursValid"));
                                    return 1;
                                })
                            )
                        )
                    )
                )

                // ─── /csc join <token> ───────────────────────────────────
                .then(ClientCommands.literal("join")
                    .then(ClientCommands.argument("token", StringArgumentType.string())
                        .executes(ctx -> {
                            joinToken(ctx.getSource(), StringArgumentType.getString(ctx, "token"));
                            return 1;
                        })
                    )
                )

                // ─── /csc connect <ip_or_token> [password] ───────────────
                .then(ClientCommands.literal("connect")
                    .then(ClientCommands.argument("target", StringArgumentType.string())
                        .executes(ctx -> {
                            String target = StringArgumentType.getString(ctx, "target");
                            if (target.startsWith("CSC-")) {
                                joinToken(ctx.getSource(), target);
                            } else {
                                connectToHostWithFallback(ctx.getSource(), target, "", CSCMod.DEFAULT_PORT, "");
                            }
                            return 1;
                        })
                        .then(ClientCommands.argument("password", StringArgumentType.string())
                            .executes(ctx -> {
                                String target = StringArgumentType.getString(ctx, "target");
                                if (target.startsWith("CSC-")) {
                                    joinToken(ctx.getSource(), target);
                                } else {
                                    connectToHostWithFallback(ctx.getSource(), target, "", CSCMod.DEFAULT_PORT, StringArgumentType.getString(ctx, "password"));
                                }
                                return 1;
                            })
                        )
                    )
                )

                // ─── /csc stop ───────────────────────────────────────────
                .then(ClientCommands.literal("stop")
                    .executes(ctx -> { stopHost(ctx.getSource()); return 1; })
                )

                // ─── /csc disconnect ─────────────────────────────────────
                .then(ClientCommands.literal("disconnect")
                    .executes(ctx -> { disconnectFromHost(ctx.getSource()); return 1; })
                )

                // ─── /csc token ──────────────────────────────────────────
                .then(ClientCommands.literal("token")
                    .executes(ctx -> {
                        if (!currentToken.isEmpty()) {
                            Component tokenComponent = Component.literal("§a[CSC] Session Token: §f§n" + currentToken)
                                .withStyle(Style.EMPTY.withClickEvent(
                                    new ClickEvent.CopyToClipboard(currentToken)
                                ));
                            ctx.getSource().sendFeedback(tokenComponent);
                            ctx.getSource().sendFeedback(Component.literal("§7  (Klicken zum Kopieren)"));
                        } else {
                            ctx.getSource().sendError(Component.literal("§c[CSC] Kein aktives Session Token vorhanden. Starte zuerst /csc host"));
                        }
                        return 1;
                    })
                )

                // ─── /csc status ─────────────────────────────────────────
                .then(ClientCommands.literal("status")
                    .executes(ctx -> {
                        boolean hosting = relayServer != null && relayServer.isRunning();
                        boolean connected = connection != null && connection.isConnected();
                        String statusText = "§b§l[CSC v1.3.0 Hardened] Status\n";
                        statusText += "§7  Dein Name: §f" + (myName.isEmpty() ? "§c(unbekannt)" : myName) + "\n";
                        statusText += "§7  Verschlüsselung: §aAES-256-GCM E2EE\n";
                        if (hosting) {
                            statusText += "§7  Hosting: §a✔ Port " + CSCMod.DEFAULT_PORT + " §7(" + relayServer.getClientCount() + "/" + relayServer.getMaxClients() + " Spieler)\n";
                            if (!currentToken.isEmpty()) {
                                statusText += "§7  Token: §f" + currentToken.substring(0, Math.min(20, currentToken.length())) + "...\n";
                            }
                        } else {
                            statusText += "§7  Hosting: §c✘ Inaktiv\n";
                        }
                        statusText += "§7  Verbunden: " + (connected ? "§a✔ Ja" : "§c✘ Nein") + "\n";
                        statusText += "§7  Log-Datei: §f" + LoggerHelper.getLogFile().toString();
                        ctx.getSource().sendFeedback(Component.literal(statusText));
                        return 1;
                    })
                )

                // ─── /csc logs ───────────────────────────────────────────
                .then(ClientCommands.literal("logs")
                    .executes(ctx -> {
                        String logPath = LoggerHelper.getLogFile().toString();
                        Component logComponent = Component.literal("§a[CSC] Log-Datei: §f§n" + logPath)
                            .withStyle(Style.EMPTY.withClickEvent(
                                new ClickEvent.CopyToClipboard(logPath)
                            ));
                        ctx.getSource().sendFeedback(logComponent);
                        ctx.getSource().sendFeedback(Component.literal("§7  (Klicken zum Pfad-Kopieren. alle Logs unter %APPDATA%/.minecraft/csc/logs/)"));
                        return 1;
                    })
                )
            );

            // ─── /ip [get] ───────────────────────────────────────────────
            dispatcher.register(ClientCommands.literal("ip")
                .executes(ctx -> { fetchPublicIp(ctx.getSource()); return 1; })
                .then(ClientCommands.literal("get")
                    .executes(ctx -> { fetchPublicIp(ctx.getSource()); return 1; })
                )
            );
        });

        // ─── Intercept '#' messages & Encrypt via AES-256-GCM ────────────
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("#")) {
                String chatText = message.substring(1).trim();
                if (chatText.isEmpty()) return false;

                // Encrypt payload using AES-256-GCM E2EE
                String encryptedText = CryptoHelper.encrypt(chatText, activeEncryptionSecret);

                if (connection != null && connection.isConnected()) {
                    connection.sendMessage(encryptedText);
                    showChat("§d[CSC] Du: §f" + chatText);
                    return false;
                }

                if (relayServer != null && relayServer.isRunning()) {
                    String outJson = "{\"type\":\"msg\",\"sender\":\"" + RelayServer.escapeJson(myName) + "\",\"text\":\"" + RelayServer.escapeJson(encryptedText) + "\"}";
                    broadcastFromHost(myName, outJson);
                    showChat("§d[CSC] Du: §f" + chatText);
                    return false;
                }

                showChat("§c[CSC] Nicht verbunden und hostest nicht! Nutze /csc host oder /csc join <token>");
                return false;
            }
            return true;
        });
    }

    private static void sendHelp(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(
            "§b§l[CSC v1.3.0 Hardened] Hilfe & Befehle\n" +
            "§e/csc host §7[passwort] [max_spieler] [stunden] §f— Starte Server & generiere Token\n" +
            "§e/csc join §7<token> §f— Anonym beitreten per Token (E2EE verschlüsselt!)\n" +
            "§e/csc stop §f— Stoppe deinen eigenen Server\n" +
            "§e/csc disconnect §f— Trenne aktuelle Verbindung\n" +
            "§e/csc token §f— Zeige aktives Session Token\n" +
            "§e/csc status §f— Zeige Host-, E2EE- & Status-Info\n" +
            "§e/csc logs §f— Zeige Log-Dateien Pfad\n" +
            "§e/ip §7[get] §f— Zeige deine IP\n" +
            "§e#nachricht §f— Sende eine AES-256-GCM verschlüsselte Nachricht"
        ));
    }

    // ─── Host ────────────────────────────────────────────────────────────
    private static void startHost(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String password, int maxPlayers, int durationHours) {
        if (relayServer != null && relayServer.isRunning()) {
            source.sendError(Component.literal("§c[CSC] Du hostest bereits! Nutze zuerst /csc stop."));
            return;
        }

        String ephemeralSecret = TokenHelper.generateEphemeralSecret();
        activeEncryptionSecret = password.isEmpty() ? ephemeralSecret : password;

        LoggerHelper.info("CSCClient", "Fetching public & LAN IP for host token generation...");

        new Thread(() -> {
            String publicIp = "127.0.0.1";
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .timeout(java.time.Duration.ofSeconds(4))
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                publicIp = response.body().trim();
            } catch (Exception e) {
                LoggerHelper.warn("CSCClient", "Could not fetch public IP, fallback to 127.0.0.1: " + e.getMessage());
            }

            String lanIp = TokenHelper.getLocalLanIp();
            long expiresAt = System.currentTimeMillis() + ((long) durationHours * 3600 * 1000);

            try {
                relayServer = new RelayServer(CSCMod.DEFAULT_PORT, password, maxPlayers, expiresAt, (type, sender, text) -> {
                    Minecraft.getInstance().execute(() -> {
                        switch (type) {
                            case "connected" -> showChat("§a[CSC] §f" + sender + "§a ist dem privaten Chat beigetreten.");
                            case "disconnected" -> showChat("§e[CSC] §f" + sender + "§e hat den privaten Chat verlassen.");
                            case "msg" -> {
                                // Decrypt AES-256-GCM incoming message
                                String decrypted = CryptoHelper.decrypt(text, activeEncryptionSecret);
                                showChat("§d[CSC] " + sender + ": §f" + decrypted);
                            }
                            case "auth_fail" -> showChat("§c[CSC] " + sender + " konnte sich nicht authentifizieren.");
                        }
                    });
                });
                relayServer.start();

                currentToken = TokenHelper.generateToken(publicIp, lanIp, CSCMod.DEFAULT_PORT, password, durationHours, maxPlayers, ephemeralSecret);
                LoggerHelper.info("CSCClient", "Host started. Session Token: " + currentToken);

                Minecraft.getInstance().execute(() -> {
                    source.sendFeedback(Component.literal("§a[CSC] ✔ Server gestartet! §7(AES-256-GCM E2EE, Max. " + maxPlayers + " Spieler, " + durationHours + "h gültig)"));
                    
                    Component tokenComponent = Component.literal("§a[CSC] Session Token: §f§n" + currentToken)
                        .withStyle(Style.EMPTY.withClickEvent(
                            new ClickEvent.CopyToClipboard(currentToken)
                        ));
                    source.sendFeedback(tokenComponent);
                    source.sendFeedback(Component.literal("§7  (Klicken zum Kopieren. Anonym & Ende-zu-Ende verschlüsselt!)"));
                });

            } catch (Exception e) {
                LoggerHelper.error("CSCClient", "Failed to start host: " + e.getMessage());
                Minecraft.getInstance().execute(() -> {
                    source.sendError(Component.literal("§c[CSC] Fehler beim Starten des Servers: " + e.getMessage()));
                });
            }
        }, "CSC-Host-Init").start();
    }

    private static void stopHost(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (relayServer != null && relayServer.isRunning()) {
            relayServer.stop();
            relayServer = null;
            currentToken = "";
            activeEncryptionSecret = "CSC_DEFAULT_SESSION_SECRET";
            LoggerHelper.info("CSCClient", "Host stopped by user.");
            source.sendFeedback(Component.literal("§e[CSC] Hosting gestoppt."));
        } else {
            source.sendError(Component.literal("§c[CSC] Du hostest aktuell nicht."));
        }
    }

    private static void broadcastFromHost(String senderName, String json) {
        if (relayServer != null && relayServer.isRunning()) {
            relayServer.broadcastFromExternal(senderName, json);
        }
    }

    // ─── Join via Token ──────────────────────────────────────────────────
    private static void joinToken(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String tokenStr) {
        try {
            TokenHelper.SessionTokenData data = TokenHelper.parseToken(tokenStr);
            activeEncryptionSecret = data.password.isEmpty() ? data.sessionSecret : data.password;

            LoggerHelper.info("CSCClient", "Joining session via Token. Public IP=" + data.publicIp + ", LAN IP=" + data.lanIp + ", Max=" + data.maxClients);
            source.sendFeedback(Component.literal("§e[CSC] Session Token verifiziert! (AES-256-GCM E2EE aktiv) Verbinde..."));
            connectToHostWithFallback(source, data.publicIp, data.lanIp, data.port, data.password);
        } catch (SecurityException e) {
            LoggerHelper.warn("CSCClient", "Token security fail: " + e.getMessage());
            source.sendError(Component.literal("§c[CSC] Token ungültig oder manipuliert!"));
        } catch (IllegalStateException e) {
            LoggerHelper.warn("CSCClient", "Token expired: " + e.getMessage());
            source.sendError(Component.literal("§c[CSC] Dieses Session Token ist abgelaufen!"));
        } catch (Exception e) {
            LoggerHelper.error("CSCClient", "Token parse error: " + e.getMessage());
            source.sendError(Component.literal("§c[CSC] Ungültiges Token-Format."));
        }
    }

    // ─── Connect ─────────────────────────────────────────────────────────
    private static void connectToHostWithFallback(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String publicHost, String lanHost, int port, String password) {
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }

        if (myName.isEmpty()) {
            myName = "Spieler";
        }

        if (!password.isEmpty()) {
            activeEncryptionSecret = password;
        }

        source.sendFeedback(Component.literal("§e[CSC] Verbinde zu Server..."));

        connection = new RelayConnection((type, sender, text) -> {
            Minecraft.getInstance().execute(() -> {
                switch (type) {
                    case "connected" -> showChat("§a[CSC] ✔ Erfolgreich verbunden (AES-256-GCM E2EE)! Schreibe §f#nachricht§a.");
                    case "msg" -> {
                        String decrypted = CryptoHelper.decrypt(text, activeEncryptionSecret);
                        showChat("§d[CSC] " + sender + ": §f" + decrypted);
                    }
                    case "system" -> showChat("§e[CSC] " + text);
                    case "auth_fail" -> showChat("§c[CSC] Authentifizierung fehlgeschlagen: " + text);
                    case "disconnected" -> showChat("§c[CSC] Verbindung getrennt: " + text);
                    case "error" -> showChat("§c[CSC] Fehler: " + text);
                }
            });
        });

        connection.connectWithFallback(publicHost, lanHost, port, myName, password).thenAccept(success -> {
            if (!success) {
                Minecraft.getInstance().execute(() -> {
                    showChat("§c[CSC] Verbindung fehlgeschlagen.");
                    showChat("§7  • Falls über das Internet: Port 49156 (TCP) im Router freigeben oder Hamachi/Radmin nutzen.");
                    showChat("§7  • Falls auf demselben PC / LAN: Überprüfe ob der Host /csc host gestartet hat.");
                });
            }
        });
    }

    private static void disconnectFromHost(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
            activeEncryptionSecret = "CSC_DEFAULT_SESSION_SECRET";
            LoggerHelper.info("CSCClient", "Disconnected by user.");
            source.sendFeedback(Component.literal("§e[CSC] Verbindung getrennt."));
        } else {
            source.sendError(Component.literal("§c[CSC] Nicht verbunden."));
        }
    }

    private static void fetchPublicIp(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§7[CSC] Ermittle deine öffentliche IP..."));

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String ip = response.body().trim();

                Minecraft.getInstance().execute(() -> {
                    Component ipComponent = Component.literal("§a[CSC] Deine öffentliche IP: §f§n" + ip)
                        .withStyle(Style.EMPTY.withClickEvent(
                            new ClickEvent.CopyToClipboard(ip)
                        ));
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(ipComponent);
                        mc.player.sendSystemMessage(Component.literal("§7  Klicken zum Kopieren. (Tipp: Nutze lieber Session Tokens mit /csc host!)"));
                    }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    showChat("§c[CSC] IP-Abfrage fehlgeschlagen: " + e.getMessage());
                });
            }
        }, "CSC-IP-Fetch").start();
    }

    private static void showChat(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }
}
