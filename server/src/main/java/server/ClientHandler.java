package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sessão de um cliente. Lê mensagens em loop, despacha para os handlers,
 * mantém heartbeat via PING/PONG e faz cleanup ao desconectar.
 */
public class ClientHandler implements Runnable {

    // espelhados no cliente, revalidado aqui
    static final int MAX_USERNAME = 24;
    static final int MIN_USERNAME = 3;
    static final int MAX_TEXT     = 500;

    // intervalo do PING menor que o timeout para qque os clients vivos sempre respondam a tempo
    private static final int READ_TIMEOUT_MS = 45_000;
    private static final int PING_INTERVAL_S = 20;

    private final Socket socket;
    private PrintWriter  out;
    private volatile String username;
    private volatile Room   currentRoom;

    private final ScheduledExecutorService pingScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ping-" + hashCode());
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> pingTask;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(READ_TIMEOUT_MS);

            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                String line;
                while ((line = in.readLine()) != null) {
                    handleMessage(line);
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout: " + username + " não respondeu ao PING.");
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + username);
        } finally {
            cleanup();
        }
    }

    private void handleMessage(String raw) {
        if (raw.length() > 4096) { sendMessage("ERROR|Mensagem muito longa"); return; }

        String[] parts = raw.split("\\|", 4);
        if (parts.length == 0) return;

        switch (parts[0]) {
            case "REGISTER" -> handleRegister(parts);
            case "LOGIN"    -> handleLogin(parts);
            case "JOIN"     -> handleJoin(parts);
            case "MSG"      -> handleMsg(parts);
            case "PONG"     -> {} // receber a linha já reseta o SO_TIMEOUT
            default         -> sendMessage("ERROR|Comando desconhecido");
        }
    }

    private void handleRegister(String[] parts) {
        if (parts.length < 3) { sendMessage("ERROR|Formato inválido"); return; }
        String name = parts[1].trim();
        String pass = parts[2];

        String err = validateUsername(name);
        if (err != null) { sendMessage("ERROR|" + err); return; }
        if (pass.length() < 6 || pass.length() > 64) {
            sendMessage("ERROR|Senha deve ter entre 6 e 64 caracteres"); return;
        }

        if (Database.register(name, pass)) {
            sendMessage("OK|REGISTER");
        } else {
            sendMessage("ERROR|Usuário já existe");
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 3) { sendMessage("ERROR|Formato inválido"); return; }
        if (username != null)  { sendMessage("ERROR|Já autenticado"); return; }

        String name = parts[1].trim();
        String pass = parts[2];

        if (!Database.login(name, pass)) {
            sendMessage("ERROR|Usuário ou senha inválidos");
            return;
        }

        // Impede login duplo: dois sockets com o mesmo username geram broadcasts duplicados
        if (!SessionRegistry.tryRegister(name)) {
            sendMessage("ERROR|Usuário já está conectado em outra sessão");
            return;
        }

        this.username = name;
        sendMessage("OK|LOGIN");
        sendMessage("ROOMS|" + String.join(",", Database.getRooms()));

        // Heartbeat começa só após login — não faz sentido pingar cliente não autenticado
        startHeartbeat();
    }

    private void handleJoin(String[] parts) {
        if (parts.length < 2) { sendMessage("ERROR|Formato inválido"); return; }
        if (username == null)  { sendMessage("ERROR|Não autenticado"); return; }

        String roomName = sanitize(parts[1], 64);
        if (roomName.isEmpty()) { sendMessage("ERROR|Nome de sala inválido"); return; }

        // Rejeita salas não cadastradas no banco — evita salas fantasma em memória
        if (!Database.roomExists(roomName)) {
            sendMessage("ERROR|Sala não existe"); return;
        }

        Room oldRoom = currentRoom;
        if (oldRoom != null) {
            oldRoom.removeClient(this);
            oldRoom.broadcast("[" + username + " saiu]", null);
        }

        // histórico buscado antes de entrar na sala — evita receber a mesma mensagem
        // pelo broadcast e pelo histórico ao mesmo tempo
        var history = Database.getHistory(roomName, 50);

        Room newRoom = Room.getOrCreate(roomName);
        newRoom.addClient(this);
        currentRoom = newRoom;

        StringBuilder sb = new StringBuilder("HISTORY|").append(roomName);
        for (String entry : history) sb.append("|").append(entry);
        sendMessage(sb.toString());

        newRoom.broadcast("[" + username + " entrou]", this);
    }

    private void handleMsg(String[] parts) {
        if (parts.length < 4) { sendMessage("ERROR|Formato inválido"); return; }

        // copia os volatiles para variáveis locais — leitura consistente dentro do método
        String u = username;
        Room   r = currentRoom;
        if (u == null || r == null) {
            sendMessage("ERROR|Não autenticado ou sem sala"); return;
        }

        // Valida sala para impedir que o cliente escreva em sala em que não está
        String roomName = parts[1];
        if (!r.getName().equals(roomName)) {
            sendMessage("ERROR|Sala inválida"); return;
        }

        String text = sanitize(parts[3], MAX_TEXT);
        if (text.isEmpty()) { sendMessage("ERROR|Mensagem vazia"); return; }

        Database.saveMessage(roomName, u, text);
        r.broadcast("MSG|" + roomName + "|" + u + "|" + text, null);
    }

    public void sendMessage(String message) {
        PrintWriter w = out;
        if (w != null) w.println(message);
    }

    // -- heartbeat -------------------------------------------------------------

    private void startHeartbeat() {
        pingTask = pingScheduler.scheduleAtFixedRate(
            () -> sendMessage("PING"),
            PING_INTERVAL_S, PING_INTERVAL_S, TimeUnit.SECONDS);
    }

    // -- cleanup ---------------------------------------------------------------

    private void cleanup() {
        if (pingTask != null) pingTask.cancel(false);
        pingScheduler.shutdown();

        // Libera o username para que o usuário possa fazer login novamente
        SessionRegistry.unregister(username);

        Room r = currentRoom;
        if (r != null) {
            r.removeClient(this);
            String u = username;
            if (u != null) r.broadcast("[" + u + " desconectou]", null);
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    // -- helpers ---------------------------------------------------------------

    /** @return mensagem de erro, ou null se válido */
    private static String validateUsername(String name) {
        if (name.length() < MIN_USERNAME || name.length() > MAX_USERNAME)
            return "Nome deve ter entre " + MIN_USERNAME + " e " + MAX_USERNAME + " caracteres";
        if (name.contains("|"))
            return "Nome não pode conter o caractere '|'";
        return null;
    }

    /** Remove pipes e trunca — cliente também sanitiza, mas não podemos confiar nisso. */
    private static String sanitize(String s, int maxLen) {
        String result = s.replace("|", "").trim();
        return result.length() > maxLen ? result.substring(0, maxLen) : result;
    }
}
