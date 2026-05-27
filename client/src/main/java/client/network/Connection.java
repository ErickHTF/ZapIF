package client.network;

import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Conexão TCP com o servidor. Conecta em background, entrega mensagens na thread JavaFX
 * tambem reconecta automaticamente após queda.
 */
public class Connection {

    public enum Status { CONNECTING, CONNECTED, DISCONNECTED }

    // alterar HOST ao fazer deploy em produção
    private static final String HOST            = "localhost";
    private static final int    PORT            = 5000;
    private static final int    RETRY_SECONDS   = 5;

    // servidor envia PING a cada 20s - 60s, para ajudar nos logs
    private static final int    READ_TIMEOUT_MS = 60_000;

    // listas de listeners — seguras para iterar enquanto outra thread adiciona/remove
    private final List<MessageListener>          msgListeners    = new CopyOnWriteArrayList<>();
    private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();

    // scheduler usado para agendar retries após queda de conexão
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-retry");
        t.setDaemon(true);
        return t;
    });

    // volatile porque são escritos na thread de conexão e lidos na thread JavaFX
    private volatile Socket      socket;
    private volatile PrintWriter out;
    private volatile boolean     running = true;

    public Connection() {
        connectAsync();
    }

    // -- api pública -----------------------------------------------------------

    // copia out para variável local antes de usar — evita NPE se a conexão cair no meio
    public void send(String message) {
        PrintWriter w = out;
        if (w != null) w.println(message);
    }

    public void addMessageListener(MessageListener l)           { msgListeners.add(l); }
    public void removeMessageListener(MessageListener l)         { msgListeners.remove(l); }
    public void addStatusListener(ConnectionStatusListener l)    { statusListeners.add(l); }
    public void removeStatusListener(ConnectionStatusListener l) { statusListeners.remove(l); }

    public void disconnect() {
        running = false;
        scheduler.shutdownNow();
        closeSocket();
    }

    // -- conexão e leitura -----------------------------------------------------

    private void connectAsync() {
        Thread t = new Thread(this::connect, "chat-connect");
        t.setDaemon(true);
        t.start();
    }

    private void connect() {
        notifyStatus(Status.CONNECTING);
        Socket s = null;
        try {
            s = new Socket(HOST, PORT);
            s.setSoTimeout(READ_TIMEOUT_MS);

            // streams prontos antes de expor o socket — send() nunca pega um socket sem out
            PrintWriter    w = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));

            socket = s;
            out    = w;
            notifyStatus(Status.CONNECTED);
            readLoop(r);

        } catch (IOException e) {
            if (s != null) try { s.close(); } catch (IOException ignored) {}
            notifyStatus(Status.DISCONNECTED);
            scheduleRetry();
        }
    }

    // parte do heartbeat — intercepta PING e responde PONG de forma transparente
    private void readLoop(BufferedReader in) {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                if ("PING".equals(line)) {
                    send("PONG");
                    continue;
                }
                final String msg = line;
                Platform.runLater(() -> msgListeners.forEach(l -> l.onMessage(msg)));
            }
        } catch (SocketTimeoutException e) {
            // nenhum dado recebido no tempo limite — servidor provavelmente caiu
        } catch (IOException ignored) {}

        if (running) {
            notifyStatus(Status.DISCONNECTED);
            closeSocket();
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (!running) return;
        scheduler.schedule(this::connectAsync, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    private void closeSocket() {
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
            socket = null;
            out    = null;
        }
    }

    // notificações sempre na thread JavaFX — listeners podem atualizar a UI diretamente
    private void notifyStatus(Status status) {
        Platform.runLater(() -> statusListeners.forEach(l -> l.onStatusChange(status)));
    }
}
