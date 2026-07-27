package dev.csc.client;

import dev.csc.CSCMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
 * CSC — Clientside Chat v1.6.0 Ultra-Compact Token & Command Alias Edition
 *
 * Ultra-compact Session Tokens (~130 chars) easily fit within Minecraft's 256 char chat limit.
 * /csc join and /csc connect are unified aliases accepting both Tokens and IP addresses.
 */
public class CSCClient implements ClientModInitializer {
    private static RelayServer relayServer;
    private static RelayConnection connection;
    private static String myName = "";
    private static String currentToken = "";

    @Override
    public void onInitializeClient() {
        LoggerHelper.info("CSCClient", "Initializing CSC v1.6.0 (Ultra-Compact Tokens & Unified Commands)...");

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (myName.isEmpty() && client.player != null) {
                myName = client.player.getName().getString();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(ClientCommands.literal("csc")
                .executes(ctx -> { sendHelp(ctx.getSource()); return 1; })

                .then(ClientCommands.literal("help")
                    .executes(ctx -> { sendHelp(ctx.getSource()); return 1; })
                )

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

                // Unified /csc join command (handles both Tokens & IP addresses)
                .then(ClientCommands.literal("join")
                    .then(ClientCommands.argument("target", StringArgumentType.string())
                        .executes(ctx -> {
                            handleJoinOrConnect(ctx.getSource(), StringArgumentType.getString(ctx, "target"), "");
                            return 1;
                        })
                        .then(ClientCommands.argument("password", StringArgumentType.string())
                            .executes(ctx -> {
                                handleJoinOrConnect(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "target"),
                                    StringArgumentType.getString(ctx, "password"));
                                return 1;
                            })
                        )
                    )
                )

                // Unified /csc connect command alias (handles both Tokens & IP addresses)
                .then(ClientCommands.literal("connect")
                    .then(ClientCommands.argument("target", StringArgumentType.string())
                        .executes(ctx -> {
                            handleJoinOrConnect(ctx.getSource(), StringArgumentType.getString(ctx, "target"), "");
                            return 1;
                        })
                        .then(ClientCommands.argument("password", StringArgumentType.string())
                            .executes(ctx -> {
                                handleJoinOrConnect(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "target"),
                                    StringArgumentType.getString(ctx, "password"));
                                return 1;
                            })
                        )
                    )
                )

                .then(ClientCommands.literal("stop")
                    .executes(ctx -> { stopHost(ctx.getSource()); return 1; })
                )

                .then(ClientCommands.literal("disconnect")
                    .executes(ctx -> { disconnectFromHost(ctx.getSource()); return 1; })
                )

                .then(ClientCommands.literal("token")
                    .executes(ctx -> {
                        if (!currentToken.isEmpty()) {
                            Component tokenComponent = Component.translatable("csc.chat.token_label", currentToken)
                                .withStyle(Style.EMPTY.withClickEvent(
                                    new ClickEvent.CopyToClipboard(currentToken)
                                ));
                            ctx.getSource().sendFeedback(tokenComponent);
                            ctx.getSource().sendFeedback(Component.translatable("csc.chat.token_sub"));
                        } else {
                            ctx.getSource().sendError(Component.translatable("csc.chat.no_active_token"));
                        }
                        return 1;
                    })
                )

                .then(ClientCommands.literal("status")
                    .executes(ctx -> {
                        boolean hosting = relayServer != null && relayServer.isRunning();
                        boolean connected = connection != null && connection.isConnected();
                        String statusText = "§b§l[CSC v1.6.0 Ultra-Compact] Status\n";
                        statusText += "§7  Name: §f" + (myName.isEmpty() ? "§c(unknown)" : myName) + "\n";
                        statusText += "§7  Key Exchange: §aECDH (secp256r1)\n";
                        statusText += "§7  Key Pinning: §a✔ Active (MitM Protection)\n";
                        statusText += "§7  Privacy Logs: §a✔ Anonymized & Masked\n";
                        statusText += "§7  Encryption: §aAES-256-GCM E2EE\n";
                        if (hosting) {
                            statusText += "§7  Hosting: §a✔ Port " + CSCMod.DEFAULT_PORT + " §7(" + relayServer.getClientCount() + "/" + relayServer.getMaxClients() + ")\n";
                            if (!currentToken.isEmpty()) {
                                statusText += "§7  Token: §f" + currentToken.substring(0, Math.min(20, currentToken.length())) + "...\n";
                            }
                        } else {
                            statusText += "§7  Hosting: §c✘ Inactive\n";
                        }
                        statusText += "§7  Connected: " + (connected ? "§a✔ Yes" : "§c✘ No") + "\n";
                        statusText += "§7  Log File: §f" + LoggerHelper.getLogFile().toString();
                        ctx.getSource().sendFeedback(Component.literal(statusText));
                        return 1;
                    })
                )

                .then(ClientCommands.literal("logs")
                    .executes(ctx -> {
                        String logPath = LoggerHelper.getLogFile().toString();
                        Component logComponent = Component.literal("§a[CSC] Log File: §f§n" + logPath)
                            .withStyle(Style.EMPTY.withClickEvent(
                                new ClickEvent.CopyToClipboard(logPath)
                            ));
                        ctx.getSource().sendFeedback(logComponent);
                        return 1;
                    })
                )
            );

            dispatcher.register(ClientCommands.literal("ip")
                .executes(ctx -> { fetchPublicIp(ctx.getSource()); return 1; })
                .then(ClientCommands.literal("get")
                    .executes(ctx -> { fetchPublicIp(ctx.getSource()); return 1; })
                )
            );
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("#")) {
                String chatText = message.substring(1).trim();
                if (chatText.isEmpty()) return false;

                if (connection != null && connection.isConnected()) {
                    connection.sendMessage(chatText);
                    showChat("§d[CSC] You: §f" + chatText);
                    return false;
                }

                if (relayServer != null && relayServer.isRunning()) {
                    String outJson = "{\"type\":\"msg\",\"sender\":\"" + RelayServer.escapeJson(myName) + "\",\"text\":\"" + RelayServer.escapeJson(chatText) + "\"}";
                    broadcastFromHost(myName, outJson);
                    showChat("§d[CSC] You: §f" + chatText);
                    return false;
                }

                showComponent(Component.translatable("csc.chat.not_connected"));
                return false;
            }
            return true;
        });
    }

    private static void sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§b§l").append(Component.translatable("csc.help.title")).append("\n")
            .append(Component.translatable("csc.help.host")).append("\n")
            .append(Component.translatable("csc.help.join")).append("\n")
            .append(Component.translatable("csc.help.stop")).append("\n")
            .append(Component.translatable("csc.help.disconnect")).append("\n")
            .append(Component.translatable("csc.help.token")).append("\n")
            .append(Component.translatable("csc.help.status")).append("\n")
            .append(Component.translatable("csc.help.logs")).append("\n")
            .append(Component.translatable("csc.help.ip")).append("\n")
            .append(Component.translatable("csc.help.message")));
    }

    private static void startHost(FabricClientCommandSource source, String password, int maxPlayers, int durationHours) {
        if (relayServer != null && relayServer.isRunning()) {
            source.sendError(Component.translatable("csc.chat.already_hosting"));
            return;
        }

        final int finalMaxPlayers = Math.max(2, Math.min(50, maxPlayers));
        final int finalDurationHours = Math.max(1, Math.min(168, durationHours));

        LoggerHelper.info("CSCClient", "Generating ECDH Host KeyPair...");

        new Thread(() -> {
            try {
                ECDHHelper.ECDHKeyPair hostKeyPair = ECDHHelper.generateKeyPair();
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
                long expiresAt = System.currentTimeMillis() + ((long) finalDurationHours * 3600 * 1000);

                relayServer = new RelayServer(CSCMod.DEFAULT_PORT, password, finalMaxPlayers, expiresAt, hostKeyPair, (type, sender, text) -> {
                    Minecraft.getInstance().execute(() -> {
                        switch (type) {
                            case "connected" -> showComponent(Component.translatable("csc.chat.joined", sender));
                            case "disconnected" -> showComponent(Component.translatable("csc.chat.left", sender));
                            case "msg" -> showChat("§d[CSC] " + sender + ": §f" + text);
                            case "auth_fail" -> showComponent(Component.translatable("csc.chat.auth_fail", sender));
                        }
                    });
                });
                relayServer.start();

                currentToken = TokenHelper.generateToken(publicIp, lanIp, CSCMod.DEFAULT_PORT, finalDurationHours, finalMaxPlayers, hostKeyPair.publicKeyBase64);
                LoggerHelper.info("CSCClient", "Host started. Compact ECDH Session Token generated: " + currentToken);

                Minecraft.getInstance().execute(() -> {
                    source.sendFeedback(Component.translatable("csc.chat.host_started", finalMaxPlayers, finalDurationHours));
                    
                    Component tokenComponent = Component.literal("§a[CSC] ")
                        .append(Component.translatable("csc.chat.token_label", currentToken))
                        .withStyle(Style.EMPTY.withClickEvent(
                            new ClickEvent.CopyToClipboard(currentToken)
                        ));
                    source.sendFeedback(tokenComponent);
                    source.sendFeedback(Component.translatable("csc.chat.token_sub"));
                });

            } catch (Exception e) {
                LoggerHelper.error("CSCClient", "Failed to start host: " + e.getMessage());
                Minecraft.getInstance().execute(() -> {
                    source.sendError(Component.literal("§c[CSC] Error starting host: " + e.getMessage()));
                });
            }
        }, "CSC-Host-Init").start();
    }

    private static void stopHost(FabricClientCommandSource source) {
        if (relayServer != null && relayServer.isRunning()) {
            relayServer.stop();
            relayServer = null;
            currentToken = "";
            LoggerHelper.info("CSCClient", "Host stopped by user.");
            source.sendFeedback(Component.translatable("csc.chat.host_stopped"));
        } else {
            source.sendError(Component.translatable("csc.chat.not_hosting"));
        }
    }

    private static void broadcastFromHost(String senderName, String json) {
        if (relayServer != null && relayServer.isRunning()) {
            relayServer.broadcastFromExternal(senderName, json);
        }
    }

    /**
     * Unified handler for both /csc join and /csc connect.
     * Automatically differentiates between a Session Token (CSC-...) and a raw IP address / Hostname.
     */
    private static void handleJoinOrConnect(FabricClientCommandSource source, String target, String password) {
        target = target.trim();
        if (target.startsWith("CSC-") || target.length() > 50) {
            joinToken(source, target, password);
        } else {
            connectToHostWithFallback(source, target, "", CSCMod.DEFAULT_PORT, password, "");
        }
    }

    private static void joinToken(FabricClientCommandSource source, String tokenStr, String overridePassword) {
        try {
            TokenHelper.SessionTokenData data = TokenHelper.parseToken(tokenStr);

            LoggerHelper.info("CSCClient", "Joining session via ECDH Token. Public IP=" + LoggerHelper.anonymizeIp(data.publicIp) + ", LAN IP=" + LoggerHelper.anonymizeIp(data.lanIp));
            source.sendFeedback(Component.translatable("csc.chat.token_verified"));
            connectToHostWithFallback(source, data.publicIp, data.lanIp, data.port, overridePassword, data.hostPubKey);
        } catch (SecurityException e) {
            LoggerHelper.warn("CSCClient", "Token security fail: " + e.getMessage());
            source.sendError(Component.translatable("csc.chat.token_invalid"));
        } catch (IllegalStateException e) {
            LoggerHelper.warn("CSCClient", "Token expired: " + e.getMessage());
            source.sendError(Component.translatable("csc.chat.token_expired"));
        } catch (Exception e) {
            LoggerHelper.error("CSCClient", "Token parse error: " + e.getMessage());
            source.sendError(Component.translatable("csc.chat.token_format_error"));
        }
    }

    private static void connectToHostWithFallback(FabricClientCommandSource source, String publicHost, String lanHost, int port, String password, String expectedHostPubKey) {
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }

        if (myName.isEmpty()) {
            myName = "Player";
        }

        source.sendFeedback(Component.translatable("csc.chat.connecting"));

        connection = new RelayConnection((type, sender, text) -> {
            Minecraft.getInstance().execute(() -> {
                switch (type) {
                    case "connected" -> showComponent(Component.translatable("csc.chat.connected"));
                    case "msg" -> showChat("§d[CSC] " + sender + ": §f" + text);
                    case "system" -> showChat("§e[CSC] " + text);
                    case "auth_fail" -> showComponent(Component.translatable("csc.chat.auth_fail", sender));
                    case "disconnected" -> showComponent(Component.translatable("csc.chat.disconnected", text));
                    case "mitm_error" -> showComponent(Component.translatable("csc.chat.mitm_alert"));
                    case "error" -> showChat("§c[CSC] " + text);
                }
            });
        });

        connection.connectWithFallback(publicHost, lanHost, port, myName, password, expectedHostPubKey).thenAccept(success -> {
            if (!success) {
                Minecraft.getInstance().execute(() -> {
                    showComponent(Component.translatable("csc.chat.connect_fail"));
                });
            }
        });
    }

    private static void disconnectFromHost(FabricClientCommandSource source) {
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
            LoggerHelper.info("CSCClient", "Disconnected by user.");
            source.sendFeedback(Component.translatable("csc.chat.user_disconnected"));
        } else {
            source.sendError(Component.translatable("csc.chat.not_connected"));
        }
    }

    private static void fetchPublicIp(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("csc.chat.fetching_ip"));

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
                    Component ipComponent = Component.literal("§a[CSC] ")
                        .append(Component.translatable("csc.chat.ip_label", ip))
                        .withStyle(Style.EMPTY.withClickEvent(
                            new ClickEvent.CopyToClipboard(ip)
                        ));
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(ipComponent);
                    }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    showComponent(Component.translatable("csc.chat.ip_fetch_fail", e.getMessage()));
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

    private static void showComponent(Component comp) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(comp);
        }
    }
}
