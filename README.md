# Clientside Chat (CSC) v1.2.0

**Private messaging across any Minecraft server — completely invisible to server admins.**

CSC lets you and your friends chat privately inside Minecraft. Messages never touch the game server — they are routed through an in-mod P2P relay with cryptographically signed Session Tokens to protect your IP address.

---

## ✨ Key Features

- 🔒 **Fully Client-Side** — Minecraft servers never see your private messages.
- 🔑 **Cryptographic Session Tokens** — Host a session and share a token like `CSC-eyJpc...`. Your IP remains hidden and token is HMAC-SHA256 signed.
- ⏳ **Time Expiration & Player Limits** — Configure token validity (e.g. 24h) and max allowed players (e.g. 2 players).
- 📝 **Dedicated Logging System** — Persistent logs saved under `%APPDATA%/.minecraft/csc/logs/csc-latest.log`.
- 🌐 **Public IP Tool** — Use `/ip get` to get your public IP with a single click to copy.
- 🌍 **Cross-Server** — Works across different servers, singleplayer, or from the main menu.
- 🪶 **Zero Dependencies** — Standard Minecraft & Java APIs only (Fabric API required).

## 📖 Quick Start

### 1. Host a Private Chat
In Minecraft, type:
```
/csc host [password] [max_players] [duration_hours]
```
*Example:* `/csc host mySecretPass 2 24`

This generates a clickable **Session Token** in chat:
`CSC-eyJpcCI6IjE5Mi4xNjguMS41IiwicG9ydCI6NDkxNTYs...`

### 2. Join a Private Chat
Click the token to copy, send it to your friend, and they join via:
```
/csc join <token>
```
*(No IP address needed!)*

### 3. Secret Chatting
Put `#` before any chat message:
```
#Hey! The server admin cannot see this message.
```

---

## 🔧 Commands

| Command | Description |
|---|---|
| `/csc host [password] [max_players] [hours]` | Host a session & generate Session Token |
| `/csc join <token>` | Join session via Session Token |
| `/csc connect <ip\|token> [password]` | Connect via raw IP or Token |
| `/csc stop` | Stop hosting |
| `/csc disconnect` | Disconnect from active session |
| `/csc token` | Show your active Session Token |
| `/csc status` | Show hosting, connection & log status |
| `/csc logs` | Show log file path (click to copy) |
| `/ip [get]` | Show your public IP |
| `/csc help` | In-game command reference |

## 📁 Log Directory

Logs are saved automatically to:
`%APPDATA%/.minecraft/csc/logs/csc-latest.log`

## ⚠️ Requirements

- Minecraft 26.2
- Fabric Loader ≥ 0.19.3
- Fabric API
