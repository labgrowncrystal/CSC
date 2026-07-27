# Clientside Chat (CSC) v1.4.1 — Security Hardened Release

**Elliptic Curve Diffie-Hellman (ECDH) & Host Public Key Pinned private messaging across any Minecraft server — completely invisible to server admins. Zero external tools required.**

CSC lets you and your friends chat privately inside Minecraft. Messages never touch the game server — they are routed through an in-mod P2P relay with zero-secret Session Tokens, ECDH Key Agreement (`secp256r1`), Host Public Key Pinning, and AES-256-GCM E2EE encryption.

---

## ✨ Security & Features

- 🔐 **Elliptic Curve Diffie-Hellman (ECDH `secp256r1`)** — Dynamic ephemeral key agreement generated live over TCP.
- 🛡️ **Host Public Key Pinning (MitM Protection)** — Clients verify the server's public key against the session token during the handshake, protecting against active Man-in-the-Middle network attacks.
- 🔒 **Zero-Secret Session Tokens** — Tokens contain no passwords, keys, or secrets (only connection routing info and Host EC public key). Token leaks cannot compromise chat privacy.
- 🔑 **Encrypted Authentication Handshake** — Passwords and auth requests are encrypted via AES-256-GCM prior to transmission. No plaintext passwords over the wire.
- ⏱️ **Constant-Time Password Comparison** — Password hashes are compared using `MessageDigest.isEqual` to prevent side-channel timing attacks.
- 🚫 **Message Rate-Limiting & Auto IP Ban** — Limits message throughput to 10 msgs/sec and automatically bans IPs for 5 minutes after 5 failed login attempts.
- 🌐 **Dual-IP Automatic Fallback** — Seamlessly connects via Public WAN IP, LAN IP, or Localhost (127.0.0.1) automatically — instant connection even on the same PC/LAN!
- 🌍 **Multi-Language Support** — Fully localized in English, German (with proper umlauts), Spanish, French, Russian, and Simplified Chinese.
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

Click the generated Session Token in chat to copy it and send it to your friend over a trusted channel (e.g. Discord DM).

### Joining

Join a friend's session using their Session Token:
```
/csc join <token>
```
No IP address needed! Key agreement & pinning are executed automatically.

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
| `/csc join <token>` | Join session via Session Token (Key Pinned & ECDH) |
| `/csc connect <ip\|token> [password]` | Connect via raw IP or Token |
| `/csc stop` | Stop hosting |
| `/csc disconnect` | Disconnect from active session |
| `/csc token` | Show your active Session Token |
| `/csc status` | Show hosting, connection, ECDH & Key Pinning status |
| `/csc logs` | Show log file path (click to copy) |
| `/ip` | Show your public IP |
| `/csc help` | In-game command reference |

---

## 📋 Requirements

- Minecraft 26.2
- Fabric Loader >= 0.19.3
- Fabric API
