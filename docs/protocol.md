# TCP protocol and storage

[← Back to README](../README.md)

## Message Protocol

Plain text, one message per line, fields separated by `|`. Lines longer than 4096 characters are rejected. Usernames containing `|` are rejected; in room names and message text the `|` is stripped.

| Message                              | Direction       | Description                                                                           |
| ------------------------------------ | --------------- | ------------------------------------------------------------------------------------- |
| `REGISTER\|name\|password`           | client → server | Sign up                                                                               |
| `LOGIN\|name\|password`              | client → server | Authenticate                                                                          |
| `JOIN\|room`                         | client → server | Join a room (leaves the previous one, if any)                                         |
| `LEAVE\|room`                        | client → server | Leave the current room (back to the home screen)                                      |
| `MSG\|room\|name\|text`              | both            | Send/receive a message — the server uses the authenticated username, not the one sent |
| `PONG`                               | client → server | Reply to `PING`                                                                       |
| `ROOMS\|room1,room2,...`             | server → client | Room list after login                                                                 |
| `ONLINE\|total\|room1:n,room2:n,...` | server → client | Online users and per-room count (after login and on every join/leave)                 |
| `HISTORY\|room\|msg1\|msg2\|...`     | server → client | Last 50 messages when joining a room (`[HH:mm] name: text`, with date separators)     |
| `PING`                               | server → client | Heartbeat every 20 s after login                                                      |
| `OK\|context`                        | server → client | Acknowledgement (`OK\|REGISTER`, `OK\|LOGIN`)                                         |
| `ERROR\|reason`                      | server → client | Descriptive error                                                                     |

Join/leave notices (`[nome entrou]`, `[nome saiu]`, `[nome desconectou]`) are sent as free-form text lines to the other members of the room.

The server closes the connection if it receives nothing for 45 s (the client automatically answers `PING` with `PONG`).

### Validated limits

| Field        | Limit                                                          |
| ------------ | -------------------------------------------------------------- |
| Username     | 3–24 characters, no `\|`                                       |
| Password     | 6–64 characters                                                |
| Message text | 1–500 characters (excess is truncated), `\|` stripped          |
| Room name    | up to 64 characters, `\|` stripped; must exist in the database |

---

## Local database

## Database (SQLite)

Tables are created automatically on first run:

```sql
usuarios  (id, nome UNIQUE, senha_hash, salt)
salas     (id, nome UNIQUE)
mensagens (id, sala_nome, usuario, texto, timestamp)
```

Passwords are stored with **PBKDF2-HMAC-SHA256** + a random per-user salt (100,000 iterations).

---
