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

public class Conexao {

    public enum Status { CONECTANDO, CONECTADO, DESCONECTADO }

    static final String HOST               = "localhost";
    static final int    PORTA              = 5001;
    static final int    SEGUNDOS_RETRY     = 5;
    static final int    TIMEOUT_LEITURA_MS = 60_000;

    private final List<OuvinteMensagem>      ouvintesMensagem = new CopyOnWriteArrayList<>();
    private final List<OuvinteStatusConexao> ouvintesStatus   = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-retry");
        t.setDaemon(true);
        return t;
    });

    private volatile Socket      socket;
    private volatile PrintWriter saida;
    private volatile boolean     ativo = true;

    public Conexao() {
        conectarAsync();
    }

    public void enviar(String mensagem) {
        PrintWriter w = saida;
        if (w != null) w.println(mensagem);
    }

    public void adicionarOuvinteMensagem(OuvinteMensagem l)    { ouvintesMensagem.add(l); }
    public void removerOuvinteMensagem(OuvinteMensagem l)      { ouvintesMensagem.remove(l); }
    public void adicionarOuvinteStatus(OuvinteStatusConexao l) { ouvintesStatus.add(l); }
    public void removerOuvinteStatus(OuvinteStatusConexao l)   { ouvintesStatus.remove(l); }

    public void desconectar() {
        ativo = false;
        agendador.shutdownNow();
        fecharSocket();
    }

    private void conectarAsync() {
        Thread t = new Thread(this::conectar, "chat-connect");
        t.setDaemon(true);
        t.start();
    }

    private void conectar() {
        notificarStatus(Status.CONECTANDO);
        Socket s = null;
        try {
            s = new Socket(HOST, PORTA);
            s.setSoTimeout(TIMEOUT_LEITURA_MS);

            PrintWriter    w = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));

            socket = s;
            saida  = w;
            notificarStatus(Status.CONECTADO);
            loopLeitura(r);

        } catch (IOException e) {
            if (s != null) try { s.close(); } catch (IOException ignorado) {}
            notificarStatus(Status.DESCONECTADO);
            agendarRetry();
        }
    }

    private void loopLeitura(BufferedReader entrada) {
        try {
            String linha;
            while (ativo && (linha = entrada.readLine()) != null) {
                if ("PING".equals(linha)) {
                    enviar("PONG");
                    continue;
                }
                final String msg = linha;
                Platform.runLater(() -> ouvintesMensagem.forEach(l -> l.aoReceberMensagem(msg)));
            }
        } catch (SocketTimeoutException e) {
        } catch (IOException ignorado) {}

        if (ativo) {
            notificarStatus(Status.DESCONECTADO);
            fecharSocket();
            agendarRetry();
        }
    }

    private void agendarRetry() {
        if (!ativo) return;
        agendador.schedule(this::conectarAsync, SEGUNDOS_RETRY, TimeUnit.SECONDS);
    }

    private void fecharSocket() {
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignorado) {}
            socket = null;
            saida  = null;
        }
    }

    private void notificarStatus(Status status) {
        Platform.runLater(() -> ouvintesStatus.forEach(l -> l.aoMudarStatus(status)));
    }
}
