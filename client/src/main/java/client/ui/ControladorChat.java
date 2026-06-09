package client.ui;

import client.Main;
import client.model.Mensagem;
import client.network.Conexao;
import client.network.OuvinteMensagem;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

import java.util.Arrays;

public class ControladorChat implements OuvinteMensagem {

    @FXML private ListView<String> listaSalas;
    @FXML private ListView<String> listaMensagens;
    @FXML private TextField        campoMensagem;
    @FXML private Button           botaoEnviar;
    @FXML private HBox             bannerOffline;

    private Conexao conexao;
    private String  nomeUsuario;
    private String  salaAtual;

    @FXML
    public void initialize() {
        conexao = Main.getConexao();
        conexao.adicionarOuvinteMensagem(this);

        conexao.adicionarOuvinteStatus(status -> {
            boolean offline = status == Conexao.Status.DESCONECTADO;
            Platform.runLater(() -> {
                bannerOffline.setVisible(offline);
                bannerOffline.setManaged(offline);
                botaoEnviar.setDisable(offline);
                campoMensagem.setDisable(offline);
            });
        });

        listaSalas.getSelectionModel().selectedItemProperty().addListener((obs, antiga, sala) -> {
            if (sala != null && !sala.equals(salaAtual)) entrarNaSala(sala);
        });
    }

    public void configurar(String nomeUsuario, String[] salas) {
        this.nomeUsuario = nomeUsuario;
        Platform.runLater(() -> {
            listaSalas.getItems().addAll(Arrays.asList(salas));
            if (salas.length > 0) listaSalas.getSelectionModel().select(0);
        });
    }

    private void entrarNaSala(String sala) {
        salaAtual = sala;
        Platform.runLater(() -> listaMensagens.getItems().clear());
        conexao.enviar("JOIN|" + sala);
    }

    @FXML
    private void aoEnviar() {
        String bruto = campoMensagem.getText().replace("|", "").trim();
        String texto = bruto.length() > 500 ? bruto.substring(0, 500) : bruto;

        if (texto.isEmpty() || salaAtual == null) return;
        conexao.enviar("MSG|" + salaAtual + "|" + nomeUsuario + "|" + texto);
        campoMensagem.clear();
    }

    @FXML
    private void aoPresionarTecla(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) aoEnviar();
    }

    @Override
    public void aoReceberMensagem(String mensagem) {
        String[] partes = mensagem.split("\\|", -1);
        switch (partes[0]) {
            case "MSG" -> {
                if (partes.length >= 4 && partes[1].equals(salaAtual)) {
                    adicionarLinha(new Mensagem(partes[2], partes[3]).toString());
                }
            }
            case "HISTORY" -> {
                if (partes.length >= 2 && partes[1].equals(salaAtual)) {
                    Platform.runLater(() -> {
                        listaMensagens.getItems().clear();
                        for (int i = 2; i < partes.length; i++) {
                            listaMensagens.getItems().add(partes[i]);
                        }
                        rolarParaBaixo();
                    });
                }
            }
        }
    }

    private void adicionarLinha(String linha) {
        Platform.runLater(() -> {
            listaMensagens.getItems().add(linha);
            rolarParaBaixo();
        });
    }

    private void rolarParaBaixo() {
        int ultimo = listaMensagens.getItems().size() - 1;
        if (ultimo >= 0) listaMensagens.scrollTo(ultimo);
    }
}
