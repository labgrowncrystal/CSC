# Clientside Chat (CSC) v1.9.2 — Encrypted P2P Chat for Minecraft

**Server-Admin Private P2P Messaging for Minecraft — zero server-side plugins or external web servers required.**

CSC enables private, encrypted peer-to-peer chat between Minecraft players. Messages pass directly over TCP sockets between client instances, bypassing the Minecraft game server entirely. Fully open-source under the MIT License and buildable with Gradle.

---

## ⚠️ Server Rules & Usage Disclaimer

> **Disclaimer:** CSC is designed for private communication between consenting friends. Using client-side messaging mods may violate the Terms of Service or chat policy of certain public Minecraft servers. Users are responsible for ensuring compliance with their local server rules.

---

## 🔒 Security Architecture & Features

- 🔐 **ECDH Key Agreement & AES-256-GCM** — Ephemeral Elliptic Curve Diffie-Hellman (`secp256r1`) key exchange over TCP, followed by AES-256-GCM authenticated encryption for all chat payloads.
- 🔑 **Host Public Key Pinning** — Clients verify the host's public key against the Session Token during handshake to protect against Man-in-the-Middle (MitM) network tampering.
- ⏱️ **Constant-Time Password Verification** — Optional password hashes are checked using `MessageDigest.isEqual` to mitigate side-channel timing attacks.
- 🙈 **IP Masking & Privacy Logging** — Centralized regex filters mask IP addresses (`192.168.1.***`) and tokens in `%APPDATA%/.minecraft/csc/logs/csc-latest.log`. Log files rotate automatically at 250 KB.
- 📦 **Compact Binary Session Tokens (~147 chars)** — Binary token schema containing routing details and the host's public key. Includes a 16-bit CRC checksum to catch accidental copy/paste or transcription errors.
- 💬 **Direct Private Whispering** — Send 1-on-1 encrypted whispers within group sessions using `#/msg <player> <text>` or `/csc msg <player> <text>`.
- 📋 **In-Game Session Player List** — Display active players and session state with `/csc list`.
- 🎵 **Selectable Sound Notifications** — Choose custom sound effects (`bell`, `ping`, `orb`, `click`, `anvil`, `off`) with mention pings (`@YourName`).
- 🔖 **Server Bookmarks / Favorites** — Save favorite session tokens or IPs locally (`/csc bookmark add|join|list|remove`).
- 👮 **Host Moderation Suite** — Manage sessions with `/csc kick`, `/csc ban`, `/csc unban #ID` (using unique Ban IDs to prevent anonymized IP collisions), and `/csc banlist`.
- 🧩 **Fabric ModMenu Integration** — Official metadata and link integration for Fabric ModMenu.
- 🌐 **Multi-Language Support** — Fully localized in English, German, Spanish, French, Russian, and Simplified Chinese.

---

## 📖 Quick Start

### 1. Hosting a Session

Start a host session and generate a Session Token:
```
/csc host [password] [max_players] [duration_hours]
```
*Example:* `/csc host mySecretPass 4 24`

Click `[ COPY TOKEN ]` in the chat to copy the token to your clipboard and share it with your friends.

> **Security Note:** If no password is specified when hosting, session access relies on keeping the Session Token private. Setting a password adds authentication protection.

### 2. Joining a Session

Join a host session using a Session Token, saved bookmark, or IP address:
```
/csc join <token|ip|bookmark> [password]
```

### 3. Sending Encrypted Messages

Prefix any message with `#` in normal Minecraft chat:
```
#Hey! This message goes directly over our encrypted P2P channel.
```

---

## 🔧 Commands Reference

| Command | Description |
|---|---|
| `/csc host [password] [max] [hours]` | Host a session & generate Session Token |
| `/csc join <token\|ip\|bookmark> [password]` | Join a session via Token, IP, or Bookmark |
| `/csc msg <player> <text>` | Send a direct whisper (or use `#/msg <player> <text>`) |
| `/csc list` | Display connected players in active session |
| `/csc bookmark [add\|join\|list\|remove]` | Manage favorite server bookmarks |
| `/csc sound [bell\|ping\|orb\|click\|anvil\|off]` | Change or toggle sound notifications |
| `/csc kick <player> [reason]` | Kick a player from your host session |
| `/csc ban <player> [reason]` | Ban a player's IP for 24 hours |
| `/csc unban <#ID\|ip>` | Unban an IP using its Ban ID (#1, #2...) or IP |
| `/csc banlist` | Display active banned entries with IDs |
| `/csc stop` | Stop your host server |
| `/csc disconnect` | Disconnect from active session |
| `/csc token` | Show your active Session Token |
| `/csc status` | Show hosting, connection, ECDH & key pinning status |
| `/csc logs` | Display log file path (click to copy) |
| `/ip [get]` | Fetch and display your public IPv4 address |
| `/csc help` | Show in-game command reference |

---

## 🏗️ Building from Source

Build the project locally using Gradle:
```bash
./gradlew build
```
The output JAR file will be saved in `build/libs/csc-1.9.2.jar`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
