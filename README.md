# ZapIF — Java desktop chat over TCP

A client/server chat application where people connect from a JavaFX desktop
client, join a room and exchange messages through a plain Java TCP server.

**Status:** Academic project · Programming Language 1.

[![CI](https://github.com/ErickHTF/ZapIF/actions/workflows/ci.yml/badge.svg)](https://github.com/ErickHTF/ZapIF/actions/workflows/ci.yml)

[Run locally](#run-locally) · [Development guide](docs/development.md) ·
[Architecture](docs/architecture.md) · [TCP protocol](docs/protocol.md)

| Login                                              | Room list                                                               | Live chat                                                               |
| -------------------------------------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| ![JavaFX login screen](docs/screenshots/login.png) | ![Chat client with rooms and welcome screen](docs/screenshots/home.png) | ![Live conversation in the geral chat room](docs/screenshots/salas.png) |

_The interface is in Portuguese. Screenshots show two clients exchanging messages over the running local server._

## Overview

ZapIF explores socket communication, server concurrency and desktop UI
programming through a small chat application.

- Register an account and sign in from the JavaFX client.
- Join the default `geral`, `off-topic` and `ajuda` rooms.
- Exchange messages and view recent room history.
- See online users and room activity.
- Reconnect automatically if the server connection drops.

## Stack

| Layer               | Technologies                                       |
| ------------------- | -------------------------------------------------- |
| Desktop client      | Java 17, JavaFX 21, FXML                           |
| Chat server         | Java 17, TCP sockets, bounded executor             |
| Storage             | SQLite                                             |
| Build and packaging | Maven Wrapper, JavaFX Maven plugin, jlink/jpackage |
| Verification        | Maven build, GitHub Actions                        |

## Run locally

Install **JDK 17+**. Maven comes through the included wrapper.

```bash
git clone https://github.com/ErickHTF/ZapIF.git
cd ZapIF
./mvnw package
```

Start the server in one terminal:

```bash
java -jar server/target/zapif-server-1.0-SNAPSHOT.jar
```

Start the JavaFX client in another terminal:

```bash
./mvnw -pl client javafx:run
```

Register an account in the client, then sign in and choose a room. Open another
client to try a conversation. The server creates `chat.db` in its current
working directory. See the [development guide](docs/development.md) for network
configuration, packaging and troubleshooting.

Plain text, one message per line, fields separated by `|`. Lines longer than 4096 characters are rejected. Usernames containing `|` are rejected; in room names and message text the `|` is stripped.

| Message | Direction | Description |
|---|---|---|
| `REGISTER\|name\|password` | client → server | Sign up |
| `LOGIN\|name\|password` | client → server | Authenticate |
| `JOIN\|room` | client → server | Join a room (leaves the previous one, if any) |
| `LEAVE\|room` | client → server | Leave the current room (back to the home screen) |
| `MSG\|room\|name\|text` | both | Send/receive a message — the server uses the authenticated username, not the one sent |
| `PONG` | client → server | Reply to `PING` |
| `ROOMS\|room1,room2,...` | server → client | Room list after login |
| `ONLINE\|total\|room1:n,room2:n,...` | server → client | Online users and per-room count (sent to that client after login, and broadcast on every join/leave/disconnect) |
| `HISTORY\|room\|msg1\|msg2\|...` | server → client | Last 50 messages when joining a room (`[HH:mm] name: text`, with date separators) |
| `PING` | server → client | Heartbeat every 20 s after login |
| `OK\|context` | server → client | Acknowledgement (`OK\|REGISTER`, `OK\|LOGIN`) |
| `ERROR\|reason` | server → client | Descriptive error |

Join/leave notices (`[nome entrou]`, `[nome saiu]`, `[nome desconectou]`) are sent as free-form text lines to the other members of the room. The current client ignores these lines.

The server closes the connection if it receives nothing for 45 s (the client automatically answers `PING` with `PONG`).

### Validated limits

| Field | Limit |
|---|---|
| Username | 3–24 characters, no `\|` |
| Password | 6–64 characters |
| Message text | 1–500 characters (excess is truncated), `\|` stripped |
| Room name | up to 64 characters, `\|` stripped; must exist in the database |

---

## Database (SQLite)

Tables are created automatically on first run:

```sql
usuarios  (id, nome UNIQUE, senha_hash, salt)
salas     (id, nome UNIQUE)
mensagens (id, sala_nome, usuario, texto, timestamp)
```

Passwords are stored with **PBKDF2-HMAC-SHA256** + a random per-user salt (100,000 iterations).

---

## Client Architecture

```
JavaFX thread (UI)              "chat-connect" thread (daemon)
├── ControladorLogin            ├── Socket.connect()
├── ControladorChat             ├── readLine() loop
└── reacts to Platform.runLater() └── Platform.runLater() → notifies listeners
```

### Connection states

```
CONECTANDO → CONECTADO → (drop) → DESCONECTADO → retry 5s → CONECTANDO ...
```

The red "Sem conexão — reconectando..." banner appears in `DESCONECTADO` and disappears once reconnected.

---

## Packaging for Distribution

### Server (fat jar)

```bash
./mvnw verify
```

The project builds three Maven modules: the root aggregator, TCP server and
JavaFX client. The repository currently has no automated test sources.

## Architecture

```mermaid
flowchart LR
    A[JavaFX client A] <-->|TCP line protocol| S[Java server]
    B[JavaFX client B] <-->|TCP line protocol| S
    S -->|JDBC| DB[(SQLite)]
```

Each client has a socket connection handled by the server's executor. The server
authenticates users, tracks active rooms and broadcasts messages to room members.
The client reads socket events off the JavaFX thread and dispatches UI updates
through `Platform.runLater`.

See the [architecture guide](docs/architecture.md) for client connection states
and implementation decisions. The [protocol guide](docs/protocol.md) documents
message framing and local storage.

## Documentation

| Guide                                        | Contents                                                      |
| -------------------------------------------- | ------------------------------------------------------------- |
| [Development](docs/development.md)           | Build, run, host configuration, packaging and troubleshooting |
| [Architecture](docs/architecture.md)         | Client/server flow and design decisions                       |
| [TCP protocol and storage](docs/protocol.md) | Message types, field limits and SQLite schema                 |
| [UML diagrams](uml/)                         | Scope, use cases, packages and sequence diagrams              |

## Project context

Developed for the Programming Language 1 course. The project focuses on learning
how TCP sockets, concurrent clients and JavaFX interfaces work together.

No license is currently included in this repository.
