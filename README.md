# Clientside Chat (CSC) v1.3.0 Hardened Edition

**AES-256-GCM End-to-End Encrypted private messaging across any Minecraft server — completely invisible to server admins. Zero external tools required.**

CSC lets you and your friends chat privately inside Minecraft. Messages never touch the game server — they are routed through an in-mod P2P relay with cryptographically signed Session Tokens and AES-256-GCM E2EE encryption.

---

## ✨ Security & Key Features

- 🛡️ **AES-256-GCM E2E Encryption** — All chat messages over the wire are encrypted using AES-256-GCM with PBKDF2 SHA256 key derivation.
- 🔑 **Dynamic Ephemeral Secrets** — Host sessions generate fresh 256-bit session secrets. No static hardcoded keys!
- 🚫 **Brute-Force Rate Limiting** — Automatically bans IPs for 5 minutes after 5 consecutive failed login attempts.
- 🔒 **Fully Client-Side** — Minecraft servers never see your private messages.
- 🌐 **Dual-IP Automatic Fallback** — Seamlessly connects via Public WAN IP, LAN IP, or Localhost (127.0.0.1) automatically — instant connection even on the same PC/LAN!
- ⏳ **Expiration & Player Limits** — Configure token validity (e.g. 24h) and max allowed players (e.g. 2 players).
- 📝 **Dedicated Logging System** — Persistent logs saved under `%APPDATA%/.minecraft/csc/logs/csc-latest.log`.
- 🪶 **Zero Dependencies** — Standard Minecraft & Java APIs only (Fabric API required).

---

## 📖 Quick Start

### Hosting

Start a private session and get a Session Token:
```
/csc host [password] [max_players] [duration_hours]
```
Example: `/csc host mySecretPass 2 24`

Click the generated Session Token in chat to copy it and send it to your friend.

### Joining

Join a friend's session using their Session Token:
```
/csc join <token>
```
No IP address needed! Encrypted automatically.

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
| `/csc join <token>` | Join session via Session Token |
| `/csc connect <ip\|token> [password]` | Connect via raw IP or Token |
| `/csc stop` | Stop hosting |
| `/csc disconnect` | Disconnect from active session |
| `/csc token` | Show your active Session Token |
| `/csc status` | Show hosting, connection, E2EE & log status |
| `/csc logs` | Show log file path (click to copy) |
| `/ip` | Show your public IP |
| `/csc help` | In-game command reference |

---

## 📋 Requirements

- Minecraft 26.2
- Fabric Loader >= 0.19.3
- Fabric API
