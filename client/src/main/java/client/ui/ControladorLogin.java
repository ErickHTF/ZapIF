package client.ui;

import client.Main;
import client.network.Conexao;
import client.network.OuvinteMensagem;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ControladorLogin implements OuvinteMensagem {

    private static final int SEGUNDOS_TIMEOUT = 10;

    @FXML private TextField     campoUsuario;
    @FXML private PasswordField campoSenha;
    @FXML private Label         labelStatus;
    @FXML private Button        botaoLogin;
    @FXML private Button        botaoCadastro;

    private Conexao conexao;

    private final ScheduledExecutorService agendador =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "login-timeout");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> timeoutPendente;

    @FXML
    public void initialize() {
        conexao = Main.getConexao();
        conexao.adicionarOuvinteMensagem(this);

        conexao.adicionarOuvinteStatus(status -> {
            switch (status) {
                case CONECTANDO   -> definirStatus("Conectando...", false);
                case CONECTADO    -> definirStatus("Conectado", true);
                case DESCONECTADO -> {
                    cancelarTimeout();
                    definirStatus("Sem conexão — tentando reconectar...", false);
                }
            }
        });
    }

    @FXML
    private void aoFazerLogin() {
        String nome  = campoUsuario.getText().trim();
        String senha = campoSenha.getText();

        String erro = validar(nome, senha);
        if (erro != null) { definirStatus(erro, true); return; }

        definirOcupado(true);
        agendarTimeout();
        conexao.enviar("LOGIN|" + nome + "|" + senha);
    }

    @FXML
    private void aoRegistrar() {
        String nome  = campoUsuario.getText().trim();
        String senha = campoSenha.getText();

        String erro = validar(nome, senha);
        if (erro != null) { definirStatus(erro, true); return; }

        definirOcupado(true);
        agendarTimeout();
        conexao.enviar("REGISTER|" + nome + "|" + senha);
    }

    @Override
    public void aoReceberMensagem(String mensagem) {
        String[] partes = mensagem.split("\\|", 3);
        switch (partes[0]) {
            case "OK" -> {
                cancelarTimeout();
                if (partes.length > 1 && "REGISTER".equals(partes[1])) {
                    definirStatus("Cadastro realizado! Faça login", true);
                }
            }
            case "ROOMS" -> {
                cancelarTimeout();
                String[] salas = (partes.length > 1 && !partes[1].isEmpty())
                        ? partes[1].split(",") : new String[0];
                abrirChat(campoUsuario.getText().trim(), salas);
            }
            case "ERROR" -> {
                cancelarTimeout();
                definirStatus(partes.length > 1 ? partes[1] : "Erro desconhecido", true);
            }
        }
    }

    private void abrirChat(String nomeUsuario, String[] salas) {
        conexao.removerOuvinteMensagem(this);
        agendador.shutdown();
        Platform.runLater(() -> {
            try {
                FXMLLoader carregador = new FXMLLoader(getClass().getResource("/client/ui/chat.fxml"));
                Scene cena = new Scene(carregador.load());

                ControladorChat ctrl = carregador.getController();
                ctrl.configurar(nomeUsuario, salas);

                Stage palco = (Stage) campoUsuario.getScene().getWindow();
                palco.setResizable(true);
                palco.setScene(cena);
                palco.setTitle("ZapIF — " + nomeUsuario);
            } catch (Exception e) {
                e.printStackTrace();
                definirStatus("Erro ao abrir o chat", true);
            }
        });
    }

    private static String validar(String nome, String senha) {
        if (nome.isEmpty() || senha.isEmpty()) return "Preencha todos os campos";
        if (nome.length() < 3 || nome.length() > 24)
            return "Nome deve ter entre 3 e 24 caracteres";
        if (nome.contains("|")) return "Nome não pode conter o caractere '|'";
        if (senha.length() < 6) return "Senha deve ter pelo menos 6 caracteres";
        return null;
    }

    private void agendarTimeout() {
        cancelarTimeout();
        timeoutPendente = agendador.schedule(
            () -> definirStatus("Servidor não respondeu — tente novamente", true),
            SEGUNDOS_TIMEOUT, TimeUnit.SECONDS);
    }

    private void cancelarTimeout() {
        if (timeoutPendente != null) {
            timeoutPendente.cancel(false);
            timeoutPendente = null;
        }
    }

    private void definirStatus(String texto, boolean habilitarBotoes) {
        Platform.runLater(() -> {
            labelStatus.setText(texto);
            definirOcupado(!habilitarBotoes);
        });
    }

    private void definirOcupado(boolean ocupado) {
        botaoLogin.setDisable(ocupado);
        botaoCadastro.setDisable(ocupado);
    }
}
