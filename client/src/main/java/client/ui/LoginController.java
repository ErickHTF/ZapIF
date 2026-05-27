package client.ui;

import client.Main;
import client.network.Connection;
import client.network.MessageListener;
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

public class LoginController implements MessageListener {

    private static final int TIMEOUT_SECONDS = 10;

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         statusLabel;
    @FXML private Button        loginButton;
    @FXML private Button        registerButton;

    private Connection connection;

    // scheduler separado da Connection para não interferir nos retries de rede
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "login-timeout");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> pendingTimeout;

    @FXML
    public void initialize() {
        connection = Main.getConnection();
        connection.addMessageListener(this);

        // parte do status de conexão, o DISCONNECTED cancela timeout ativo pois o servidor não vai responder
        connection.addStatusListener(status -> {
            switch (status) {
                case CONNECTING   -> setStatus("Conectando...", false);
                case CONNECTED    -> setStatus("Conectado", true);
                case DISCONNECTED -> {
                    cancelTimeout();
                    setStatus("Sem conexão — tentando reconectar...", false);
                }
            }
        });
    }

    @FXML
    private void onLogin() {
        String name = usernameField.getText().trim();
        String pass = passwordField.getText();

        String err = validate(name, pass);
        if (err != null) { setStatus(err, true); return; }

        setBusy(true);
        scheduleTimeout();
        connection.send("LOGIN|" + name + "|" + pass);
    }

    @FXML
    private void onRegister() {
        String name = usernameField.getText().trim();
        String pass = passwordField.getText();

        String err = validate(name, pass);
        if (err != null) { setStatus(err, true); return; }

        setBusy(true);
        scheduleTimeout();
        connection.send("REGISTER|" + name + "|" + pass);
    }

    // Respostas do servidor
    @Override
    public void onMessage(String message) {
        String[] parts = message.split("\\|", 3);
        switch (parts[0]) {
            case "OK" -> {
                cancelTimeout();
                if (parts.length > 1 && "REGISTER".equals(parts[1])) {
                    setStatus("Cadastro realizado! Faça login", true);
                }
            }
            case "ROOMS" -> {
                cancelTimeout();
                String[] rooms = (parts.length > 1 && !parts[1].isEmpty())
                        ? parts[1].split(",") : new String[0];
                openChat(usernameField.getText().trim(), rooms);
            }
            case "ERROR" -> {
                cancelTimeout();
                setStatus(parts.length > 1 ? parts[1] : "Erro desconhecido", true);
            }
        }
    }

    // parte da troca de cena, se remove como listener antes de abrir o chat
    // para que as mensagens seguintes sejam tratadas pelo ChatController
    private void openChat(String username, String[] rooms) {
        connection.removeMessageListener(this);
        scheduler.shutdown();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/chat.fxml"));
                Scene scene = new Scene(loader.load());

                ChatController ctrl = loader.getController();
                ctrl.setup(username, rooms);

                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setResizable(true);
                stage.setScene(scene);
                stage.setTitle("ZapIF — " + username);
            } catch (Exception e) {
                e.printStackTrace();
                setStatus("Erro ao abrir o chat", true);
            }
        });
    }

    // -- helpers ---------------------------------------------------------------

    // validação no cliente para evitar uso do servidor em erros muito simples
    private static String validate(String name, String pass) {
        if (name.isEmpty() || pass.isEmpty()) return "Preencha todos os campos";
        if (name.length() < 3 || name.length() > 24)
            return "Nome deve ter entre 3 e 24 caracteres";
        if (name.contains("|")) return "Nome não pode conter o caractere '|'";
        if (pass.length() < 6)  return "Senha deve ter pelo menos 6 caracteres";
        return null;
    }

    // double ckick cancela o timer anterior e reinicia do zero
    private void scheduleTimeout() {
        cancelTimeout();
        pendingTimeout = scheduler.schedule(
            () -> setStatus("Servidor não respondeu — tente novamente", true),
            TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelTimeout() {
        if (pendingTimeout != null) {
            pendingTimeout.cancel(false);
            pendingTimeout = null;
        }
    }

    private void setStatus(String text, boolean enableButtons) {
        Platform.runLater(() -> {
            statusLabel.setText(text);
            setBusy(!enableButtons);
        });
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
    }
}
