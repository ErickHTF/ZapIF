package client;

import client.network.Conexao;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    // Estático para que os controladores possam acessar a conexão via Main.getConexao()
    // Existe uma única Conexao durante todo o ciclo de vida do app.
    private static Conexao conexao;

    @Override
    public void start(Stage palco) throws Exception {
        // Abre a conexão antes de carregar o FXML: quando ControladorLogin.initialize() rodar,
        // a Conexao já existe e pode receber ouvintes imediatamente.
        conexao = new Conexao();

        var iconStream = getClass().getResourceAsStream("/client/icon.png");
        if (iconStream != null) palco.getIcons().add(new Image(iconStream));

        FXMLLoader carregador = new FXMLLoader(getClass().getResource("/client/ui/login.fxml"));
        palco.setScene(new Scene(carregador.load()));
        palco.setTitle("ZapIF");
        palco.setResizable(false);
        palco.show();
    }

    @Override
    public void stop() {
        // stop() é chamado pelo JavaFX ao fechar a janela principal.
        // Encerra a Conexao para interromper o loopLeitura e liberar o socket.
        if (conexao != null) conexao.desconectar();
    }

    public static Conexao getConexao() {
        return conexao;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
