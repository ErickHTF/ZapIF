# ZapIF — Scope Document

## 1. Overview

ZapIF is a real-time chat system built on a TCP client-server architecture. The server is a plain Java process with a SQLite database; the client is a JavaFX desktop application. Communication uses a simple `|`-delimited text protocol.

---

## 2. Screens (User Interfaces)

### Screen 1 — Login / Sign-up (`login.fxml`)

**Goal:** authenticate or register a user before accessing the chat.

| Element | Type | Description |
|---|---|---|
| "Nome de usuário" (Username) field | TextField | 3–24 characters, no `\|` |
| "Senha" (Password) field | PasswordField | 6–64 characters |
| "Entrar" (Log in) button | Button | Sends `LOGIN\|name\|password` to the server |
| "Cadastrar" (Sign up) button | Button | Sends `REGISTER\|name\|password` to the server |
| Status label | Label | Shows validation errors, connection state and server feedback |

**Flows:**
- Successful sign-up → label shows "Cadastro realizado! Faça login" (Signed up! Please log in)
- Successful login → automatic transition to Screen 2
- Error (invalid credentials, user already exists) → label shows the error message
- No connection → buttons disabled, label shows "Sem conexão — tentando reconectar..." (No connection — trying to reconnect...)
- 10 s timeout with no response → label shows a warning and re-enables the buttons

---

### Screen 2 — Chat (`chat.fxml`)

**Goal:** let the user send and receive messages in chat rooms.

| Element | Type | Description |
|---|---|---|
| Room list | ListView | Shows the available rooms; clicking switches rooms |
| Message list | ListView | Shows the history and real-time messages of the active room |
| Message field | TextField | Text to send; `Enter` or the button sends it |
| "Enviar" (Send) button | Button | Sends `MSG\|room\|user\|text` |
| Offline banner | HBox (red) | Appears when the connection drops; disappears on reconnect |

**Flows:**
- On entering the screen → room 0 selected automatically, history loaded
- Switching rooms → message list cleared, new history loaded
- Message received → appended to the end of the list, auto-scroll to the bottom
- Connection drops → red banner appears, field and button disabled
- Reconnection → banner disappears, field and button re-enabled

---

## 3. Use Cases

| ID | Name | Actor | Summary |
|---|---|---|---|
| UC01 | Sign up | Unauthenticated user | Creates a new account with name and password |
| UC02 | Log in | Unauthenticated user | Authenticates with existing credentials |
| UC03 | View rooms | Authenticated user | Sees the list of available rooms |
| UC04 | Join room | Authenticated user | Selects a room and loads its history |
| UC05 | Send message | Authenticated user | Sends text to the current room |
| UC06 | Receive message in real time | Authenticated user | Receives messages from other users via broadcast |
| UC07 | View history | Authenticated user | Loads the last 50 messages when joining the room |
| UC08 | Switch rooms | Authenticated user | Leaves the current room and joins another |
| UC09 | Disconnect | Authenticated user | Closes the client; session released on the server |
| UC10 | Validate credentials | Server | Checks name/password with PBKDF2 against the database |
| UC11 | Persist message | Server | Saves the message to SQLite before broadcasting |
| UC12 | Broadcast message | Server | Sends the message to everyone in the room |
| UC13 | PING/PONG heartbeat | Server | Detects idle clients every 20 s |
| UC14 | Reconnect automatically | Client | Retries connecting to the server every 5 s after a drop |

---

## 4. Architecture

### Layered view

```
┌─────────────────────────────────────────────────────┐
│                  CLIENT (JavaFX)                    │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │  client  │  │ client.ui    │  │ client.model  │ │
│  │  .Main   │  │ ControladorL │  │ Mensagem      │ │
│  │          │  │ ControladorC │  │ Usuario       │ │
│  └──────────┘  └──────────────┘  └───────────────┘ │
│                ┌──────────────────────────────────┐ │
│                │       client.network             │ │
│                │  Conexao  OuvinteMensagem         │ │
│                │           OuvinteStatusConexao   │ │
│                └──────────────────────────────────┘ │
└───────────────────────┬─────────────────────────────┘
                        │ TCP Socket (port 5001)
                        │ text protocol with "|"
┌───────────────────────┴─────────────────────────────┐
│                  SERVER (Java SE)                   │
│  ┌───────────┐  ┌──────────────────┐  ┌──────────┐ │
│  │ Servidor  │  │ManipuladorCliente│  │  Sala    │ │
│  │ (main +   │  │ (1 thread/client)│  │(broadcast│ │
│  │  pool)    │  └──────────────────┘  │  list)   │ │
│  └───────────┘  ┌──────────────────┐  └──────────┘ │
│                 │  RegistroSessao  │               │
│                 │  BancoDados      │               │
│                 └──────────────────┘               │
│                        │ JDBC                      │
│                 ┌──────┴──────┐                    │
│                 │  SQLite     │                    │
│                 │  chat.db    │                    │
│                 └─────────────┘                    │
└─────────────────────────────────────────────────────┘
```

### Client packages

| Package | Responsibility |
|---|---|
| `client` | Entry point (`Main`), JavaFX application lifecycle |
| `client.ui` | Screen controllers (FXML + presentation logic) |
| `client.network` | TCP connection, automatic retry, message delivery on the JavaFX thread |
| `client.model` | Domain objects (`Mensagem`, `Usuario`) |

### Server package

| Package / class | Responsibility |
|---|---|
| `server` | All server components |
| `Servidor` | Entry point, `ServerSocket`, thread pool |
| `ManipuladorCliente` | One thread per client; parses the protocol, validates and routes |
| `Sala` | Groups connected clients, performs thread-safe broadcast |
| `RegistroSessao` | Single-session control (prevents double login) |
| `BancoDados` | SQLite access: users, rooms, message history |

---

## 5. Communication Protocol

Plain text separated by `|`, one message per line (`\n`).

| Message | Direction | Description |
|---|---|---|
| `REGISTER\|name\|password` | client → server | Sign up |
| `LOGIN\|name\|password` | client → server | Authenticate |
| `JOIN\|room` | client → server | Join a room |
| `MSG\|room\|name\|text` | both | Send / receive a message |
| `PONG` | client → server | Heartbeat reply |
| `OK\|context` | server → client | Acknowledgement (REGISTER, LOGIN) |
| `ROOMS\|room1,room2,...` | server → client | Room list after login |
| `HISTORY\|room\|msg1\|...` | server → client | History when joining a room |
| `PING` | server → client | Heartbeat every 20 s |
| `ERROR\|reason` | server → client | Descriptive error |

---

## 6. Database

```sql
usuarios  (id, nome UNIQUE, senha_hash, salt)
salas     (id, nome UNIQUE)
mensagens (id, sala_nome, usuario, texto, timestamp)
```

- Passwords: **PBKDF2-HMAC-SHA256** + random per-user salt (100,000 iterations)
- Default rooms created at startup: `geral`, `off-topic`, `ajuda`
- History: last 50 messages per room, returned in chronological order

---

## 7. Constraints and Limits

| Field | Rule |
|---|---|
| Username | 3–24 characters, no `\|` |
| Password | 6–64 characters |
| Message text | 1–500 characters, `\|` stripped automatically |
| Concurrent clients | up to 100 (server thread pool) |
| History per room | last 50 messages |
| Login timeout | 10 s with no response → re-enables buttons |
| Heartbeat | PING every 20 s; read timeout at 45 s |
| Connection retry | every 5 s after a drop |

---

## 8. Out of Scope (current version)

- Private messages (DMs) between users
- Users creating/deleting rooms
- File or image uploads
- Desktop notifications
- Connection encryption (TLS/SSL)
- Web or mobile interface
- OAuth / SSO authentication
- User administration (banning, moderation)
