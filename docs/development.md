# Development guide

[← Back to README](../README.md)

## Prerequisites

| Tool  | Minimum version | How to check    |
| ----- | --------------- | --------------- |
| JDK   | 17              | `java -version` |
| Maven | 3.8 (optional)  | `mvn -version`  |

The project ships with the **Maven Wrapper** (`./mvnw` on Linux/Mac, `mvnw.cmd` on Windows), which downloads Maven 3.9.6 on first run. If you'd rather use an installed Maven, replace `./mvnw` with `mvn` in the commands below. All commands run **from the repository root**.

> **Windows**: make sure `JAVA_HOME` points to JDK 17+ and that `%JAVA_HOME%\bin` is on your `PATH`. Use `mvnw.cmd` instead of `./mvnw`.

---

## Getting Started

### Run the server

Open a terminal at the project root:

```bash
./mvnw package          # builds server and client
java -jar server/target/zapif-server-1.0-SNAPSHOT.jar
```

Expected output:

```
Banco de dados pronto.
Servidor iniciado na porta 5001 (max 100 clientes)
```

> Three `SLF4J: ...` warnings from the SQLite driver may show up before these lines — they're harmless.

The `chat.db` file (SQLite) is created automatically in the current directory. Keep this terminal open — the server needs to keep running. Press `Ctrl+C` to stop it.

### Run the client

Open **another** terminal, also at the project root:

```bash
./mvnw -pl client javafx:run
```

The login window opens. Click **Cadastrar** (Sign up) to create an account, then **Entrar** (Log in).

> To open multiple clients (to simulate a conversation), run `./mvnw -pl client javafx:run` again in additional terminals.

### Connecting two computers on the same network

By default the client connects to `localhost:5001`. Pointing it at another computer **doesn't require recompiling**:

1. Find the local IP of the computer running the server:
   - Windows: `ipconfig` → "IPv4 Address"
   - Linux/Mac: `ip a` or `ifconfig`
2. On the other computers, start the client passing the IP:
   ```bash
   ./mvnw -pl client javafx:run -Dzapif.host=192.168.0.10
   ```
   Or use environment variables:
   ```bash
   ZAPIF_HOST=192.168.0.10 ./mvnw -pl client javafx:run       # Linux/Mac
   ```
   ```powershell
   $env:ZAPIF_HOST="192.168.0.10"; .\mvnw.cmd -pl client javafx:run   # Windows (PowerShell)
   ```
3. Allow port 5001 (TCP) through the firewall on the server machine.

| Setting     | Property (`-D`) | Environment variable | Default     |
| ----------- | --------------- | -------------------- | ----------- |
| Server host | `zapif.host`    | `ZAPIF_HOST`         | `localhost` |
| Server port | `zapif.port`    | `ZAPIF_PORT`         | `5001`      |

The `-D` property takes precedence over the environment variable. Outside Maven (e.g. the image built by `jlink`), pass the property straight to the JVM: `client/target/image/bin/java -Dzapif.host=192.168.0.10 -m client/client.Main`.

> The **server** port is fixed in `Servidor.PORTA` (5001); `zapif.port` only changes where the client connects.

---

## Troubleshooting

| Symptom                                                          | Likely cause                          | Fix                                                                                           |
| ---------------------------------------------------------------- | ------------------------------------- | --------------------------------------------------------------------------------------------- |
| `Unable to access jarfile server/target/...`                     | Maven didn't build it                 | Run `./mvnw package` before `java -jar`                                                       |
| "Sem conexão — reconectando..." / "Serviço: sem conexão" banner  | Server isn't running or wrong host    | Start the server first (Step 1) and check `zapif.host`                                        |
| `Erro fatal no servidor: Address already in use`                 | Another process is using port 5001    | `netstat -ano \| findstr :5001` (Windows) or `lsof -i :5001` (Linux/Mac) and kill the process |
| `UnsatisfiedLinkError` / `JavaFX runtime components are missing` | JRE without JavaFX                    | Use JDK 17+ and run via `./mvnw -pl client javafx:run`, not `java -jar`                       |
| Login hangs for 10 s                                             | Server isn't responding               | Check that the server is up and reachable                                                     |
| `Usuário já está conectado em outra sessão`                      | Same user logged in on another client | Close the other session or use a different user                                               |

---
