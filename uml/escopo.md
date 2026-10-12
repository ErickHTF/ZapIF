# ZapIF — Scope Document

## 1. Overview

ZapIF is a real-time chat system built on a TCP client-server architecture. The server is a plain Java process with a SQLite database; the client is a JavaFX desktop application. Communication uses a simple `|`-delimited text protocol.

---

## 2. Screens (User Interfaces)

### Screen 1 — Login / Sign-up (`login.fxml`)

**Goal:** authenticate or register a user before accessing the chat.

| Element | Type | Description |
|---|---|---|
| "Usuário:" (Username) field | TextField | 3–24 characters, no `\|` |
| "Senha:" (Password) field | PasswordField | 6–64 characters (the client checks the minimum, the server enforces both) |
| "Entrar" (Log in) button | Button | Sends `LOGIN\|name\|password` to the server |
| "Cadastrar" (Sign up) button | Button | Sends `REGISTER\|name\|password` to the server |
| Status label | Label | Shows validation errors and server feedback |
| Service label (bottom right) | Label | Shows the connection state: "Serviço: conectando..." (Service: connecting...), "Serviço: Online" (Service: Online) or "Serviço: sem conexão — reconectando..." (Service: no connection — reconnecting...) |

**Flows:**
- Successful sign-up → label shows "Cadastro realizado! Faça login" (Signed up! Please log in)
- Successful login → automatic transition to Screen 2 (when the `ROOMS` list arrives)
- Error (invalid credentials, user already exists, user already logged in elsewhere) → label shows the error message sent by the server
- Connecting or no connection → buttons disabled; the service label shows "Serviço: conectando..." or "Serviço: sem conexão — reconectando..."; buttons are re-enabled once it shows "Serviço: Online"
- 10 s timeout with no response → label shows "Servidor não respondeu — tente novamente" (Server did not respond — try again) and re-enables the buttons

---

### Screen 2 — Chat (`chat.fxml`)

**Goal:** let the user send and receive messages in chat rooms.

| Element | Type | Description |
|---|---|---|
| Username + "(Online)" | Label | Logged-in user's name at the top of the sidebar |
| Online counter | Label | "● n online" — total users logged in on the server (from `ONLINE`) |
| "SALAS" (Rooms) header | HBox | Click collapses/expands the room list (▼ / ▶) |
| Room list | ListView | Shows the available rooms with the number of users in each (`[n]`); clicking a room joins it |
| "< Início" (Home) button | Button | Leaves the current room (`LEAVE\|room`) and returns to the welcome panel |
| Welcome panel | VBox | "Selecione uma sala para começar a conversar." (Select a room to start chatting.) — shown until a room is selected |
| Message list | ListView | Shows the history and real-time messages of the active room |
| Message field | TextField | "Mensagem..." (Message...) placeholder; `Enter` or the button sends it |
| "Enviar" (Send) button | Button | Sends `MSG\|room\|user\|text` |
| Offline banner | HBox (red) | "Sem conexão — reconectando..." (No connection — reconnecting...); appears when the connection drops, disappears on reconnect |

**Flows:**
- On entering the screen → welcome panel shown, no room joined; the user picks a room from the list
- Selecting a room → message list cleared, chat area shown, `JOIN|room` sent, history loaded
- Switching rooms → message list cleared, new history loaded (the server removes the user from the previous room)
- Clicking "< Início" (Home) → `LEAVE|room` sent, message list cleared, welcome panel shown again
- Message received → appended to the end of the list, auto-scroll to the bottom (join/leave notices from the server are not displayed)
- Connection drops → red banner appears, field and button disabled
- Reconnection → banner disappears, field and button re-enabled; the session is not restored (no new `LOGIN`/`JOIN` is sent), so the server rejects messages until the user restarts the client and logs in again

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
| UC13 | PING/PONG heartbeat | Server | Sends PING every 20 s; drops clients silent for 45 s |
| UC14 | Reconnect automatically | Client | Retries connecting to the server every 5 s after a drop |
| UC15 | Leave room | Authenticated user | Clicks "< Início" (Home) to leave the current room and return to the welcome panel |
| UC16 | View online users | Authenticated user | Sees the total online count and the number of users in each room |

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
| `Sala` | Groups connected clients, performs thread-safe broadcast, builds per-room online counts |
| `RegistroSessao` | Single-session control (prevents double login), total online count |
| `BancoDados` | SQLite access: users, rooms, message history |

---

## 5. Communication Protocol

Plain text separated by `|`, one message per line (`\n`).

| Message | Direction | Description |
|---|---|---|
| `REGISTER\|name\|password` | client → server | Sign up |
| `LOGIN\|name\|password` | client → server | Authenticate |
| `JOIN\|room` | client → server | Join a room (leaves the previous one, if any) |
| `LEAVE\|room` | client → server | Leave the current room (back to the welcome panel) |
| `MSG\|room\|name\|text` | both | Send / receive a message — the server uses the authenticated username, not the one sent |
| `PONG` | client → server | Heartbeat reply |
| `OK\|context` | server → client | Acknowledgement (REGISTER, LOGIN) |
| `ROOMS\|room1,room2,...` | server → client | Room list after login |
| `ONLINE\|total\|room1:n,room2:n,...` | server → client | Online users and per-room count (after login and on every join/leave/disconnect) |
| `HISTORY\|room\|msg1\|...` | server → client | Last 50 messages when joining a room (`[HH:mm] name: text`, with date separators) |
| `PING` | server → client | Heartbeat every 20 s after login |
| `ERROR\|reason` | server → client | Descriptive error |

Join/leave notices (`[nome entrou]`, `[nome saiu]`, `[nome desconectou]`) are sent as free-form text lines to the other members of the room. The current client ignores these lines. Lines longer than 4096 characters are rejected.

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
| Message text | 1–500 characters (excess is truncated), `\|` stripped automatically |
| Room name | up to 64 characters, `\|` stripped; must exist in the database |
| Concurrent clients | up to 100 served at once (server thread pool); extra connections wait in the pool queue |
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
