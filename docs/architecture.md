# Architecture

[← Back to README](../README.md)

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
./mvnw -pl server package
# produces: server/target/zapif-server-1.0-SNAPSHOT.jar (with the SQLite driver bundled)
java -jar server/target/zapif-server-1.0-SNAPSHOT.jar
```

### Client (native installer)

```bash
./mvnw -pl client package javafx:jlink   # builds a runtime image in client/target/image
client/target/image/bin/java -m client/client.Main   # test the image

# installer (run on the target OS; --type: exe/msi on Windows, dmg/pkg on Mac, deb/rpm on Linux)
jpackage --runtime-image client/target/image --module client/client.Main \
         --name ZapIF --dest client/target/dist --type exe
```

---

## Design Decisions

| Decision                                   | Rationale                                       |
| ------------------------------------------ | ----------------------------------------------- |
| Thread-per-client on the server            | simple, no external framework                   |
| ExecutorService (pool of 100)              | avoids unbounded thread creation under load     |
| CopyOnWriteArrayList for room clients      | frequent reads, rare writes                     |
| PBKDF2 + salt for hashing                  | resists rainbow tables with no extra dependency |
| Platform.runLater() centralized in Conexao | controllers don't need to know about threading  |
| Limited split in the parser (`split("\\    | ", 4)`)                                         | the text field can contain `\|` without breaking parsing |
