package dev.csc.client;

import dev.csc.CSCMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * CSC — Clientside Chat v1.9.1 Security Clarity & Ban ID Edition
 *
 * Features:
 *   - Unique Ban ID System (#1, #2...) for collision-free unbanning of anonymized IPs
 *   - No-Password Session Security Notice
 *   - Direct Private Whispering (#/msg <player> <text> or /csc msg)
 *   - Session Player List (/csc list)
 *   - Selectable Notification Sounds (/csc sound bell|ping|orb|click|anvil|off)
 *   - Favorite Server Bookmarks (/csc bookmark add|join|list|remove)
 *   - Host Moderation: /csc kick, /csc ban, /csc unban, /csc banlist
 *   - Modern Chat Badges & Interactive [COPY TOKEN] button
 *   - Ultra-compact Binary Tokens (~147 chars) & Unified Commands
 */
public class CSCClient implements ClientModInitializer {
    private static RelayServer relayServer;
    private static RelayConnection connection;
    private static String myName = "";
    private static String currentToken = "";
    private static String selectedSound = "bell"; // bell, ping, orb, click, anvil, off

    @Override
    public void onInitializeClient() {
        LoggerHelper.info("CSCClient", "Initializing CSC v1.9.1 (Security Clarity & Ban ID Edition)...");

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

                .then(ClientCommands.literal("msg")
                    .then(ClientCommands.argument("target", StringArgumentType.string())
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String target = StringArgumentType.getString(ctx, "target");
                                String text = StringArgumentType.getString(ctx, "text");
                                sendDirectWhisper(ctx.getSource(), target, text);
                                return 1;
                            })
                        )
                    )
                )

                .then(ClientCommands.literal("list")
                    .executes(ctx -> {
                        showPlayerList(ctx.getSource());
                        return 1;
                    })
                )

                .then(ClientCommands.literal("bookmark")
                    .executes(ctx -> {
                        showBookmarks(ctx.getSource());
                        return 1;
                    })
                    .then(ClientCommands.literal("list")
                        .executes(ctx -> {
                            showBookmarks(ctx.getSource());
                            return 1;
                        })
                    )
                    .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("name", StringArgumentType.string())
                            .then(ClientCommands.argument("target", StringArgumentType.string())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    String target = StringArgumentType.getString(ctx, "target");
                                    if (BookmarkManager.addBookmark(name, target, "")) {
                                        ctx.getSource().sendFeedback(Component.literal("§8[§d§lCSC§8] §aBookmark '" + name + "' added successfully!"));
                                    } else {
                                        ctx.getSource().sendError(Component.literal("§8[§d§lCSC§8] §cFailed to add bookmark."));
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.argument("password", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        String target = StringArgumentType.getString(ctx, "target");
                                        String pw = StringArgumentType.getString(ctx, "password");
                                        if (BookmarkManager.addBookmark(name, target, pw)) {
                                            ctx.getSource().sendFeedback(Component.literal("§8[§d§lCSC§8] §aBookmark '" + name + "' added successfully!"));
                                        } else {
                                            ctx.getSource().sendError(Component.literal("§8[§d§lCSC§8] §cFailed to add bookmark."));
                                        }
                                        return 1;
                                    })
                                )
                            )
                        )
                    )
                    .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("name", StringArgumentType.string())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "name");
                                if (BookmarkManager.removeBookmark(name)) {
                                    ctx.getSource().sendFeedback(Component.literal("§8[§d§lCSC§8] §aBookmark '" + name + "' removed."));
                                } else {
                                    ctx.getSource().sendError(Component.literal("§8[§d§lCSC§8] §cBookmark '" + name + "' not found."));
                                }
                                return 1;
                            })
                        )
                    )
                )

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

                .then(ClientCommands.literal("sound")
                    .executes(ctx -> {
                        selectedSound = selectedSound.equals("off") ? "bell" : "off";
                        ctx.getSource().sendFeedback(Component.translatable(selectedSound.equals("off") ? "csc.chat.sound_off" : "csc.chat.sound_set", selectedSound));
                        if (!selectedSound.equals("off")) playNotificationSound(false);
                        return 1;
                    })
                    .then(ClientCommands.argument("mode", StringArgumentType.string())
                        .executes(ctx -> {
                            String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
                            if (mode.equals("off") || mode.equals("bell") || mode.equals("ping") || mode.equals("orb") || mode.equals("click") || mode.equals("anvil")) {
                                selectedSound = mode;
                                ctx.getSource().sendFeedback(Component.translatable(mode.equals("off") ? "csc.chat.sound_off" : "csc.chat.sound_set", mode));
                                if (!selectedSound.equals("off")) playNotificationSound(false);
                            } else {
                                ctx.getSource().sendError(Component.literal("§8[§d§lCSC§8] §cInvalid sound mode! Choose: bell, ping, orb, click, anvil, off"));
                            }
                            return 1;
                        })
                    )
                )

                .then(ClientCommands.literal("kick")
                    .then(ClientCommands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String p = StringArgumentType.getString(ctx, "player");
                            kickHostPlayer(ctx.getSource(), p, "");
                            return 1;
                        })
                        .then(ClientCommands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String p = StringArgumentType.getString(ctx, "player");
                                String r = StringArgumentType.getString(ctx, "reason");
                                kickHostPlayer(ctx.getSource(), p, r);
                                return 1;
                            })
                        )
                    )
                )

                .then(ClientCommands.literal("ban")
                    .then(ClientCommands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String p = StringArgumentType.getString(ctx, "player");
                            banHostPlayer(ctx.getSource(), p, "");
                            return 1;
                        })
                        .then(ClientCommands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String p = StringArgumentType.getString(ctx, "player");
                                String r = StringArgumentType.getString(ctx, "reason");
                                banHostPlayer(ctx.getSource(), p, r);
                                return 1;
                            })
                        )
                    )
                )

                .then(ClientCommands.literal("unban")
                    .then(ClientCommands.argument("target", StringArgumentType.string())
                        .executes(ctx -> {
                            String target = StringArgumentType.getString(ctx, "target");
                            unbanHostIp(ctx.getSource(), target);
                            return 1;
                        })
                    )
                )

                .then(ClientCommands.literal("banlist")
                    .executes(ctx -> {
                        showBanlist(ctx.getSource());
                        return 1;
                    })
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
                            sendTokenComponent(ctx.getSource(), currentToken);
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
                        String statusText = "§8[§d§lCSC v1.9.1§8] §bStatus Overview\n";
                        statusText += "§7  Name: §f" + (myName.isEmpty() ? "§c(unknown)" : myName) + "\n";
                        statusText += "§7  Sound Mode: " + (selectedSound.equals("off") ? "§c✘ Off 🔕" : "§a✔ " + selectedSound + " 🔔") + "\n";
                        statusText += "§7  Key Exchange: §aECDH (secp256r1)\n";
                        statusText += "§7  Key Pinning: §a✔ Active (MitM Protection)\n";
                        statusText += "§7  Privacy Logs: §a✔ Anonymized & Masked\n";
                        statusText += "§7  Encryption: §aAES-256-GCM E2EE\n";
                        statusText += "§7  Bookmarks: §f" + BookmarkManager.getAllBookmarks().size() + " saved\n";
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
                        Component logComponent = Component.literal("§8[§d§lCSC§8] §aLog File: §f§n" + logPath)
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

                // Support #/msg <target> <text> direct whisper shortcut!
                if (chatText.startsWith("/msg ")) {
                    String whisperData = chatText.substring(5).trim();
                    int firstSpace = whisperData.indexOf(' ');
                    if (firstSpace > 0) {
                        String target = whisperData.substring(0, firstSpace).trim();
                        String text = whisperData.substring(firstSpace + 1).trim();
                        if (!target.isEmpty() && !text.isEmpty()) {
                            sendDirectWhisper(null, target, text);
                            return false;
                        }
                    }
                }

                if (connection != null && connection.isConnected()) {
                    connection.sendMessage(chatText);
                    showChat("§8[§d§lCSC§8] §dYou: §f" + chatText);
                    return false;
                }

                if (relayServer != null && relayServer.isRunning()) {
                    String outJson = "{\"type\":\"msg\",\"sender\":\"" + RelayServer.escapeJson(myName) + "\",\"text\":\"" + RelayServer.escapeJson(chatText) + "\"}";
                    broadcastFromHost(myName, outJson);
                    showChat("§8[§d§lCSC§8] §dYou: §f" + chatText);
                    return false;
                }

                showComponent(Component.translatable("csc.chat.not_connected"));
                return false;
            }
            return true;
        });
    }

    private static void sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§8[§d§lCSC§8] §b").append(Component.translatable("csc.help.title")).append("\n")
            .append(Component.translatable("csc.help.host")).append("\n")
            .append(Component.translatable("csc.help.join")).append("\n")
            .append(Component.translatable("csc.help.msg")).append("\n")
            .append(Component.translatable("csc.help.list")).append("\n")
            .append(Component.translatable("csc.help.bookmark")).append("\n")
            .append(Component.translatable("csc.help.sound")).append("\n")
            .append(Component.translatable("csc.help.kick")).append("\n")
            .append(Component.translatable("csc.help.ban")).append("\n")
            .append(Component.translatable("csc.help.unban")).append("\n")
            .append(Component.translatable("csc.help.banlist")).append("\n")
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
                            case "connected" -> {
                                showComponent(Component.translatable("csc.chat.joined", sender));
                                playNotificationSound(false);
                            }
                            case "disconnected" -> showComponent(Component.translatable("csc.chat.left", sender));
                            case "msg" -> handleIncomingChatMessage(sender, text);
                            case "whisper" -> {
                                showChat("§8[§d§lCSC Whisper from " + sender + "§8] §f" + text);
                                playNotificationSound(true);
                            }
                            case "auth_fail" -> showComponent(Component.translatable("csc.chat.auth_fail", sender));
                        }
                    });
                });
                relayServer.start();

                currentToken = TokenHelper.generateToken(publicIp, lanIp, CSCMod.DEFAULT_PORT, finalDurationHours, finalMaxPlayers, hostKeyPair.publicKeyBase64);
                LoggerHelper.info("CSCClient", "Host started. Compact ECDH Session Token generated: " + currentToken);

                Minecraft.getInstance().execute(() -> {
                    source.sendFeedback(Component.translatable("csc.chat.host_started", finalMaxPlayers, finalDurationHours));
                    if (password.isEmpty()) {
                        source.sendFeedback(Component.literal("§e[CSC Notice] No password set. Your session security relies entirely on keeping the Session Token private."));
                    }
                    sendTokenComponent(source, currentToken);
                    playNotificationSound(false);
                });

            } catch (Exception e) {
                LoggerHelper.error("CSCClient", "Failed to start host: " + e.getMessage());
                Minecraft.getInstance().execute(() -> {
                    source.sendError(Component.literal("§8[§d§lCSC§8] §cError starting host: " + e.getMessage()));
                });
            }
        }, "CSC-Host-Init").start();
    }

    private static void sendDirectWhisper(FabricClientCommandSource source, String target, String text) {
        if (connection != null && connection.isConnected()) {
            connection.sendWhisper(target, text);
            showChat("§8[§d§lCSC Whisper -> " + target + "§8] §f" + text);
            return;
        }

        if (relayServer != null && relayServer.isRunning()) {
            boolean sent = relayServer.sendWhisper(myName, target, text);
            if (sent) {
                showChat("§8[§d§lCSC Whisper -> " + target + "§8] §f" + text);
            } else {
                if (source != null) {
                    source.sendError(Component.literal("§8[§d§lCSC§8] §cPlayer '" + target + "' not found in session for whisper."));
                } else {
                    showChat("§8[§d§lCSC§8] §cPlayer '" + target + "' not found in session for whisper.");
                }
            }
            return;
        }

        if (source != null) {
            source.sendError(Component.translatable("csc.chat.not_connected"));
        } else {
            showComponent(Component.translatable("csc.chat.not_connected"));
        }
    }

    private static void showPlayerList(FabricClientCommandSource source) {
        boolean hosting = relayServer != null && relayServer.isRunning();
        boolean connected = connection != null && connection.isConnected();

        if (!hosting && !connected) {
            source.sendError(Component.translatable("csc.chat.not_connected"));
            return;
        }

        StringBuilder sb = new StringBuilder("§8[§d§lCSC Session Players§8]\n");
        if (hosting) {
            List<String> clientNames = relayServer.getClientNames();
            sb.append("§7  • §a").append(myName.isEmpty() ? "Host" : myName).append(" §8(Host / Owner)\n");
            for (String name : clientNames) {
                sb.append("§7  • §f").append(name).append(" §8(Connected Player)\n");
            }
            sb.append("§7Total: §f").append(clientNames.size() + 1).append("/").append(relayServer.getMaxClients()).append(" players");
        } else {
            sb.append("§7  • §a").append(myName).append(" §8(You / Connected Client)\n");
            sb.append("§7Connected to private host via ECDH E2EE.");
        }
        source.sendFeedback(Component.literal(sb.toString()));
    }

    private static void showBookmarks(FabricClientCommandSource source) {
        Map<String, BookmarkManager.Bookmark> bms = BookmarkManager.getAllBookmarks();
        if (bms.isEmpty()) {
            source.sendFeedback(Component.literal("§8[§d§lCSC§8] §aNo bookmarks saved yet. Use /csc bookmark add <name> <token|ip>"));
        } else {
            StringBuilder sb = new StringBuilder("§8[§d§lCSC Saved Bookmarks§8]\n");
            for (BookmarkManager.Bookmark bm : bms.values()) {
                String preview = bm.target.length() > 25 ? bm.target.substring(0, 22) + "..." : bm.target;
                sb.append("§7  • §e").append(bm.name).append(" §8-> §f").append(preview).append("\n");
            }
            source.sendFeedback(Component.literal(sb.toString()));
        }
    }

    private static void kickHostPlayer(FabricClientCommandSource source, String player, String reason) {
        if (relayServer == null || !relayServer.isRunning()) {
            source.sendError(Component.translatable("csc.chat.not_hosting"));
            return;
        }
        if (relayServer.kickPlayer(player, reason)) {
            source.sendFeedback(Component.translatable("csc.chat.kicked_success", player));
        } else {
            source.sendError(Component.translatable("csc.chat.kicked_fail", player));
        }
    }

    private static void banHostPlayer(FabricClientCommandSource source, String player, String reason) {
        if (relayServer == null || !relayServer.isRunning()) {
            source.sendError(Component.translatable("csc.chat.not_hosting"));
            return;
        }
        if (relayServer.banPlayer(player, reason)) {
            source.sendFeedback(Component.translatable("csc.chat.banned_success", player));
        } else {
            source.sendError(Component.translatable("csc.chat.kicked_fail", player));
        }
    }

    private static void unbanHostIp(FabricClientCommandSource source, String target) {
        if (relayServer == null || !relayServer.isRunning()) {
            source.sendError(Component.translatable("csc.chat.not_hosting"));
            return;
        }
        if (relayServer.unbanIp(target)) {
            source.sendFeedback(Component.translatable("csc.chat.unbanned_success", target));
        } else {
            source.sendError(Component.translatable("csc.chat.unbanned_fail", target));
        }
    }

    private static void showBanlist(FabricClientCommandSource source) {
        if (relayServer == null || !relayServer.isRunning()) {
            source.sendError(Component.translatable("csc.chat.not_hosting"));
            return;
        }
        List<RelayServer.BannedEntry> bannedList = relayServer.getBannedEntries();
        if (bannedList.isEmpty()) {
            source.sendFeedback(Component.literal("§8[§d§lCSC§8] §aBanlist is currently empty."));
        } else {
            StringBuilder sb = new StringBuilder("§8[§d§lCSC§8] §cActive Banned Entries (Use /csc unban #ID):\n");
            for (RelayServer.BannedEntry entry : bannedList) {
                String anon = LoggerHelper.anonymizeIp(entry.rawIp);
                long remainingSec = (entry.expiresAt - System.currentTimeMillis()) / 1000L;
                sb.append("§7  • §e#").append(entry.id).append(" §8- §f").append(anon)
                  .append(" §8(").append(Math.max(0, remainingSec)).append("s remaining)\n");
            }
            source.sendFeedback(Component.literal(sb.toString()));
        }
    }

    private static void sendTokenComponent(FabricClientCommandSource source, String token) {
        Component tokenText = Component.literal("§8[§d§lCSC§8] ")
            .append(Component.translatable("csc.chat.token_label", token));
        source.sendFeedback(tokenText);

        Component button = Component.literal("§8[§d§lCSC§8] §a§l")
            .append(Component.translatable("csc.chat.token_copy_btn")
                .withStyle(Style.EMPTY
                    .withColor(0x55FF55)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.CopyToClipboard(token))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy Token to Clipboard")))
                )
            );
        source.sendFeedback(button);
        source.sendFeedback(Component.translatable("csc.chat.token_sub"));
    }

    private static void handleIncomingChatMessage(String sender, String text) {
        boolean isMention = !myName.isEmpty() && (text.contains("@" + myName) || text.contains(myName));
        if (isMention) {
            showChat("§8[§d§lCSC§8] §e§l" + sender + " (Mention): §f" + text);
            playNotificationSound(true);
        } else {
            showChat("§8[§d§lCSC§8] §d" + sender + ": §f" + text);
            playNotificationSound(false);
        }
    }

    private static void playNotificationSound(boolean isMention) {
        if (selectedSound.equals("off")) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                SoundEvent se = SoundEvents.NOTE_BLOCK_BELL.value();
                if (isMention) {
                    se = SoundEvents.NOTE_BLOCK_CHIME.value();
                } else {
                    switch (selectedSound) {
                        case "ping" -> se = SoundEvents.NOTE_BLOCK_CHIME.value();
                        case "orb" -> se = SoundEvents.EXPERIENCE_ORB_PICKUP;
                        case "click" -> se = SoundEvents.UI_BUTTON_CLICK.value();
                        case "anvil" -> se = SoundEvents.ANVIL_USE;
                        default -> se = SoundEvents.NOTE_BLOCK_BELL.value();
                    }
                }
                mc.getSoundManager().play(SimpleSoundInstance.forUI(se, isMention ? 1.6F : 1.2F));
            }
        } catch (Exception ignored) {}
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

    private static void handleJoinOrConnect(FabricClientCommandSource source, String target, String password) {
        target = target.trim();
        BookmarkManager.Bookmark bm = BookmarkManager.getBookmark(target);
        if (bm != null) {
            target = bm.target;
            if (password.isEmpty() && !bm.password.isEmpty()) {
                password = bm.password;
            }
        }

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
                    case "connected" -> {
                        showComponent(Component.translatable("csc.chat.connected"));
                        playNotificationSound(false);
                    }
                    case "msg" -> handleIncomingChatMessage(sender, text);
                    case "whisper" -> {
                        showChat("§8[§d§lCSC Whisper from " + sender + "§8] §f" + text);
                        playNotificationSound(true);
                    }
                    case "system" -> showChat("§8[§d§lCSC§8] §e" + text);
                    case "auth_fail" -> showComponent(Component.translatable("csc.chat.auth_fail", sender));
                    case "disconnected" -> showComponent(Component.translatable("csc.chat.disconnected", text));
                    case "mitm_error" -> showComponent(Component.translatable("csc.chat.mitm_alert"));
                    case "error" -> showChat("§8[§d§lCSC§8] §c" + text);
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
                    Component ipComponent = Component.literal("§8[§d§lCSC§8] ")
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
