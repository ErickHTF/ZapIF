package client.ui;

import client.Main;
import client.model.Message;
import client.network.Connection;
import client.network.MessageListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

import java.util.Arrays;

public class ChatController implements MessageListener {

    @FXML private ListView<String> roomList;
    @FXML private ListView<String> messageList;
    @FXML private TextField        messageField;
    @FXML private Button           sendButton;
    @FXML private HBox             offlineBanner;

    private Connection connection;
    private String username;
    private String currentRoom;

    @FXML
    public void initialize() {
        connection = Main.getConnection();
        connection.addMessageListener(this);

        connection.addStatusListener(status -> {
            boolean offline = status == Connection.Status.DISCONNECTED;
            Platform.runLater(() -> {
                // setManaged junto com setVisible evita que o banner reserve espaço no layout
                offlineBanner.setVisible(offline);
                offlineBanner.setManaged(offline);
                sendButton.setDisable(offline);
                messageField.setDisable(offline);
            });
        });

        // Evita o JOIN duplicado se o usuário clicar numa sala já ativa
        roomList.getSelectionModel().selectedItemProperty().addListener((obs, old, room) -> {
            if (room != null && !room.equals(currentRoom)) joinRoom(room);
        });
    }

    /** Chamado pelo LoginController antes de exibir a cena*/
    public void setup(String username, String[] rooms) {
        this.username = username;
        Platform.runLater(() -> {
            roomList.getItems().addAll(Arrays.asList(rooms));
            if (rooms.length > 0) roomList.getSelectionModel().select(0);
        });
    }

    private void joinRoom(String room) {
        currentRoom = room;
        Platform.runLater(() -> messageList.getItems().clear());
        connection.send("JOIN|" + room);
    }

    @FXML
    private void onSend() {
        // '|' quebraria o protocolo, remove aqui e o servidor remove por segurança
        String raw  = messageField.getText().replace("|", "").trim();
        String text = raw.length() > 500 ? raw.substring(0, 500) : raw;

        if (text.isEmpty() || currentRoom == null) return;
        connection.send("MSG|" + currentRoom + "|" + username + "|" + text);
        messageField.clear();
        // A mensagem aparece quando o servidor faz broadcast de volta (exclude=null),
        // garantindo que o que o usuário vê é o que foi persistido
    }

    @FXML
    private void onKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) onSend();
    }

    @Override
    public void onMessage(String message) {
        // split -1 preserva partes vazias no final — necessário para histórico correto
        String[] parts = message.split("\\|", -1);
        switch (parts[0]) {
            case "MSG" -> {
                // Descarta mensagens de outras salas que chegam em trânsito durante troca de sala
                if (parts.length >= 4 && parts[1].equals(currentRoom)) {
                    appendLine(new Message(parts[2], parts[3]).toString());
                }
            }
            case "HISTORY" -> {
                // Guard com currentRoom: descarta histórico de sala anterior que chega com atraso
                if (parts.length >= 2 && parts[1].equals(currentRoom)) {
                    Platform.runLater(() -> {
                        messageList.getItems().clear();
                        for (int i = 2; i < parts.length; i++) {
                            messageList.getItems().add(parts[i]);
                        }
                        scrollToBottom();
                    });
                }
            }
        }
    }

    private void appendLine(String line) {
        Platform.runLater(() -> {
            messageList.getItems().add(line);
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        int last = messageList.getItems().size() - 1;
        if (last >= 0) messageList.scrollTo(last);
    }
}
