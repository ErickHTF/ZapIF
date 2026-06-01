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
 * mantém heartbeat via PING/PONG e faz limpeza ao desconectar.
 */
public class ManipuladorCliente implements Runnable {

    // espelhados no cliente, revalidado aqui
    static final int MAX_NOME_USUARIO = 24;
    static final int MIN_NOME_USUARIO = 3;
    static final int MAX_TEXTO        = 500;

    // intervalo do PING menor que o timeout para que os clientes vivos sempre respondam a tempo
    static final int TIMEOUT_LEITURA_MS = 45_000;
    static final int INTERVALO_PING_S   = 20;

    private final Socket socket;
    private PrintWriter  saida;
    private volatile String nomeUsuario;
    private volatile Sala   salaAtual;

    private final ScheduledExecutorService agendadorPing =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ping-" + hashCode());
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> tarefaPing;

    public ManipuladorCliente(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(TIMEOUT_LEITURA_MS);

            try (BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

                saida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                String linha;
                while ((linha = entrada.readLine()) != null) {
                    processarMensagem(linha);
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout: " + nomeUsuario + " não respondeu ao PING.");
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + nomeUsuario);
        } finally {
            limpar();
        }
    }

    private void processarMensagem(String bruto) {
        if (bruto.length() > 4096) { enviarMensagem("ERROR|Mensagem muito longa"); return; }

        String[] partes = bruto.split("\\|", 4);
        if (partes.length == 0) return;

        switch (partes[0]) {
            case "REGISTER" -> processarRegistro(partes);
            case "LOGIN"    -> processarLogin(partes);
            case "JOIN"     -> processarEntradaSala(partes);
            case "MSG"      -> processarMensagemChat(partes);
            case "PONG"     -> {} // receber a linha já reseta o SO_TIMEOUT
            default         -> enviarMensagem("ERROR|Comando desconhecido");
        }
    }

    private void processarRegistro(String[] partes) {
        if (partes.length < 3) { enviarMensagem("ERROR|Formato inválido"); return; }
        String nome = partes[1].trim();
        String senha = partes[2];

        String erro = validarNomeUsuario(nome);
        if (erro != null) { enviarMensagem("ERROR|" + erro); return; }
        if (senha.length() < 6 || senha.length() > 64) {
            enviarMensagem("ERROR|Senha deve ter entre 6 e 64 caracteres"); return;
        }

        if (BancoDados.registrar(nome, senha)) {
            enviarMensagem("OK|REGISTER");
        } else {
            enviarMensagem("ERROR|Usuário já existe");
        }
    }

    private void processarLogin(String[] partes) {
        if (partes.length < 3) { enviarMensagem("ERROR|Formato inválido"); return; }
        if (nomeUsuario != null) { enviarMensagem("ERROR|Já autenticado"); return; }

        String nome = partes[1].trim();
        String senha = partes[2];

        if (!BancoDados.autenticar(nome, senha)) {
            enviarMensagem("ERROR|Usuário ou senha inválidos");
            return;
        }

        // Impede login duplo: dois sockets com o mesmo username geram broadcasts duplicados
        if (!RegistroSessao.tentarRegistrar(nome)) {
            enviarMensagem("ERROR|Usuário já está conectado em outra sessão");
            return;
        }

        this.nomeUsuario = nome;
        enviarMensagem("OK|LOGIN");
        enviarMensagem("ROOMS|" + String.join(",", BancoDados.obterSalas()));

        // Heartbeat começa só após login — não faz sentido pingar cliente não autenticado
        iniciarHeartbeat();
    }

    private void processarEntradaSala(String[] partes) {
        if (partes.length < 2) { enviarMensagem("ERROR|Formato inválido"); return; }
        if (nomeUsuario == null) { enviarMensagem("ERROR|Não autenticado"); return; }

        String nomeSala = sanitizar(partes[1], 64);
        if (nomeSala.isEmpty()) { enviarMensagem("ERROR|Nome de sala inválido"); return; }

        // Rejeita salas não cadastradas no banco — evita salas fantasma em memória
        if (!BancoDados.salaExiste(nomeSala)) {
            enviarMensagem("ERROR|Sala não existe"); return;
        }

        Sala salaAntiga = salaAtual;
        if (salaAntiga != null) {
            salaAntiga.removerCliente(this);
            salaAntiga.transmitir("[" + nomeUsuario + " saiu]", null);
        }

        // histórico buscado antes de entrar na sala — evita receber a mesma mensagem
        // pelo broadcast e pelo histórico ao mesmo tempo
        var historico = BancoDados.obterHistorico(nomeSala, 50);

        Sala novaSala = Sala.obterOuCriar(nomeSala);
        novaSala.adicionarCliente(this);
        salaAtual = novaSala;

        StringBuilder sb = new StringBuilder("HISTORY|").append(nomeSala);
        for (String entrada : historico) sb.append("|").append(entrada);
        enviarMensagem(sb.toString());

        novaSala.transmitir("[" + nomeUsuario + " entrou]", this);
    }

    private void processarMensagemChat(String[] partes) {
        if (partes.length < 4) { enviarMensagem("ERROR|Formato inválido"); return; }

        // copia os volatiles para variáveis locais — leitura consistente dentro do método
        String u = nomeUsuario;
        Sala   s = salaAtual;
        if (u == null || s == null) {
            enviarMensagem("ERROR|Não autenticado ou sem sala"); return;
        }

        // Valida sala para impedir que o cliente escreva em sala em que não está
        String nomeSala = partes[1];
        if (!s.getNome().equals(nomeSala)) {
            enviarMensagem("ERROR|Sala inválida"); return;
        }

        String texto = sanitizar(partes[3], MAX_TEXTO);
        if (texto.isEmpty()) { enviarMensagem("ERROR|Mensagem vazia"); return; }

        BancoDados.salvarMensagem(nomeSala, u, texto);
        s.transmitir("MSG|" + nomeSala + "|" + u + "|" + texto, null);
    }

    public void enviarMensagem(String mensagem) {
        PrintWriter w = saida;
        if (w != null) w.println(mensagem);
    }

    // -- heartbeat -------------------------------------------------------------

    private void iniciarHeartbeat() {
        tarefaPing = agendadorPing.scheduleAtFixedRate(
            () -> enviarMensagem("PING"),
            INTERVALO_PING_S, INTERVALO_PING_S, TimeUnit.SECONDS);
    }

    // -- limpeza ---------------------------------------------------------------

    private void limpar() {
        if (tarefaPing != null) tarefaPing.cancel(false);
        agendadorPing.shutdown();

        // Libera o nome de usuário para que o usuário possa fazer login novamente
        RegistroSessao.desregistrar(nomeUsuario);

        Sala s = salaAtual;
        if (s != null) {
            s.removerCliente(this);
            String u = nomeUsuario;
            if (u != null) s.transmitir("[" + u + " desconectou]", null);
        }
        try { socket.close(); } catch (IOException ignorado) {}
    }

    // -- helpers ---------------------------------------------------------------

    /** @return mensagem de erro, ou null se válido */
    private static String validarNomeUsuario(String nome) {
        if (nome.length() < MIN_NOME_USUARIO || nome.length() > MAX_NOME_USUARIO)
            return "Nome deve ter entre " + MIN_NOME_USUARIO + " e " + MAX_NOME_USUARIO + " caracteres";
        if (nome.contains("|"))
            return "Nome não pode conter o caractere '|'";
        return null;
    }

    /** Remove pipes e trunca — cliente também sanitiza, mas não podemos confiar nisso. */
    private static String sanitizar(String s, int tamanhoMax) {
        String resultado = s.replace("|", "").trim();
        return resultado.length() > tamanhoMax ? resultado.substring(0, tamanhoMax) : resultado;
    }
}
