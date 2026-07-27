# Clientside Chat (CSC) v1.7.0 — Audio Feedback & Modern UI Badge Edition

**Server-Admin Private P2P Messaging for Minecraft — zero server-side plugins or external web servers required.**

CSC enables private, encrypted peer-to-peer chat between Minecraft players. Chat messages never touch the Minecraft game server and are invisible to server admins. Fully open-source and reproducibly buildable with Gradle under the MIT License.

---

## ⚠️ Server Rules Disclaimer

> **Disclaimer:** CSC is designed for private communication between consenting friends on private servers. Using client-side private messaging mods may violate the Terms of Service or chat policy of certain public Minecraft servers. Users are solely responsible for ensuring compliance with local server rules.

---

## ✨ Features & Security Architecture

- 🔔 **In-Game Sound Notifications & Mention Pings** — Plays subtle Minecraft sound effects on incoming private `#` messages and distinct chime pings when mentioned (`@YourName`).
- 🎨 **Modern Chat Badges & Interactive Copy Button** — Features stylized color-coded chat prefix badges (`[CSC]`) and interactive `[ COPY TOKEN ]` buttons with hover tooltips.
- 📦 **Ultra-Compact Session Tokens (~147 chars)** — Versioned binary token schema with CRC16 tamper verification that easily fits inside Minecraft's 256-character chat limit with 100+ characters of headroom.
- 🛠️ **100% Reproducible Build System** — Full Gradle build pipeline (`build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`) in the repository for clean compilation across Windows, macOS, and Linux.
- 📄 **MIT Open Source License** — Fully open-source code under the MIT License.
- 🛡️ **Server-Admin Private P2P** — Chat messages bypass the game server entirely via direct P2P TCP sockets.
- 🔐 **Elliptic Curve Diffie-Hellman (ECDH `secp256r1`)** — Dynamic ephemeral key agreement generated live over TCP.
- 🔑 **Host Public Key Pinning (MitM Protection)** — Clients verify the server's public key against the session token during the handshake, protecting against active Man-in-the-Middle network attacks.
- 🔒 **Zero-Secret Session Tokens** — Tokens contain no passwords, keys, or secrets (only connection routing info and Host EC public key). Token leaks cannot compromise chat privacy.
- 🙈 **Universal Regex IP Masking & Token Redaction** — Centralized regex filters automatically mask IP addresses (`192.168.1.***`) and session tokens in `%APPDATA%/.minecraft/csc/logs/csc-latest.log`. Log files rotate automatically at 250 KB.
- ⏱️ **Constant-Time Password Comparison** — Password hashes are verified using `MessageDigest.isEqual` to prevent side-channel timing attacks.
- 🎭 **Password-First Auth & Anti-Spoofing** — Prevents active player name enumeration and duplicate player name spoofing within sessions.
- 🚫 **Message Rate-Limiting & Auto IP Ban** — Limits message throughput to 10 msgs/sec and automatically bans IPs for 5 minutes after 5 failed login attempts.
- 🌐 **Dual-IP Automatic Fallback** — Seamlessly connects via Public WAN IP, LAN IP, or Localhost (127.0.0.1) automatically.
- 🌍 **Multi-Language Support** — Fully localized in English, German (with proper umlauts), Spanish, French, Russian, and Simplified Chinese.

---

## 📖 Quick Start

### Hosting

Start a private session and get a Session Token:
```
/csc host [password] [max_players] [duration_hours]
```
Example: `/csc host mySecretPass 2 24`

Click the `[ COPY TOKEN ]` button in chat to copy the Session Token to your clipboard and send it to your friend.

### Joining

Join a friend's session using their Session Token or direct IP:
```
/csc join <token|ip> [password]
```

### Secret Chatting

Put `#` before any chat message:
```
#Hey! The server admin cannot see this message.
```

---

## 🔧 Commands

| Command | Description |
|---|---|
| `/csc host [password] [max] [hours]` | Host a session & generate Session Token |
| `/csc join <token\|ip> [password]` | Join session via Session Token or IP |
| `/csc connect <token\|ip> [password]` | Alias for `/csc join` |
| `/csc sound [on\|off]` | Toggle notification sounds & mention pings |
| `/csc stop` | Stop hosting |
| `/csc disconnect` | Disconnect from active session |
| `/csc token` | Show your active Session Token |
| `/csc status` | Show hosting, connection, ECDH & Key Pinning status |
| `/csc logs` | Show log file path (click to copy) |
| `/ip` | Show your public IP |
| `/csc help` | In-game command reference |

---

## 🏗️ Building from Source

Build the project locally using Gradle:
```bash
./gradlew build
```
The compiled JAR artifact will be located under `build/libs/csc-1.7.0.jar`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
