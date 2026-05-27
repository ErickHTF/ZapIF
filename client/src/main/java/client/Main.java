package client;

import client.network.Connection;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    // Estático para que os controllers possam acessar a conexão via Main.getConnection()
    // Existe uma única Connection durante todo o ciclo de vida do app.
    private static Connection connection;

    @Override
    public void start(Stage stage) throws Exception {
        // Abre a conexão antes de carregar o FXML: quando LoginController.initialize() rodar, a Connection já existe
        // e pode receber listeners imediatamente.
        connection = new Connection();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/login.fxml"));
        stage.setScene(new Scene(loader.load()));
        stage.setTitle("Chat");
        stage.setResizable(false); // login tem tamanho fixo; chat.fxml habilita resize
        stage.show();
    }

    @Override
    public void stop() {
        // stop() é chamado pelo JavaFX ao fechar a janela principal.
        // Encerra a Connection para interromper o readLoop e liberar o socket.
        if (connection != null) connection.disconnect();
    }

    public static Connection getConnection() {
        return connection;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
