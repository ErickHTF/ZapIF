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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ControladorChat implements OuvinteMensagem {

    @FXML private ListView<String> listaSalas;
    @FXML private ListView<String> listaMensagens;
    @FXML private TextField        campoMensagem;
    @FXML private Button           botaoEnviar;
    @FXML private HBox             bannerOffline;
    @FXML private VBox             painelBemVindo;
    @FXML private VBox             painelChat;
    @FXML private Label            labelTotalOnline;
    @FXML private Label            labelNomeUsuario;

    private Conexao conexao;
    private String  nomeUsuario;
    private String  salaAtual;
    private final Map<String, Integer> contagemPorSala = new HashMap<>();

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

        listaSalas.setCellFactory(lv -> new ListCell<>() {
            private final Label labelNome   = new Label();
            private final Label labelCount  = new Label();
            private final Region spacer     = new Region();
            private final HBox  caixa       = new HBox(labelNome, spacer, labelCount);
            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                labelNome.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 14px;");
                labelCount.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 13px;");
                caixa.setStyle("-fx-padding: 0; -fx-alignment: CENTER_LEFT;");
                setStyle("-fx-padding: 8 14;");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setMouseTransparent(true);
                    setFocusTraversable(false);
                } else {
                    Integer n = contagemPorSala.get(item);
                    labelNome.setText(item);
                    labelCount.setText(n != null ? "[" + n + "]" : "");
                    labelCount.setStyle("-fx-font-size: 13px; -fx-text-fill: "
                            + (n != null && n > 0 ? "#a6e3a1" : "#6c7086") + ";");
                    setGraphic(caixa);
                    setMouseTransparent(false);
                    setFocusTraversable(true);
                }
            }
        });

        listaSalas.getSelectionModel().selectedItemProperty().addListener((obs, antiga, sala) -> {
            if (sala != null && !sala.equals(salaAtual)) entrarNaSala(sala);
        });
    }

    public void configurar(String nomeUsuario, String[] salas) {
        this.nomeUsuario = nomeUsuario;
        Platform.runLater(() -> {
            labelNomeUsuario.setText(nomeUsuario);
            listaSalas.getItems().addAll(Arrays.asList(salas));
        });
    }

    private void entrarNaSala(String sala) {
        salaAtual = sala;
        Platform.runLater(() -> {
            listaMensagens.getItems().clear();
            painelBemVindo.setVisible(false);
            painelBemVindo.setManaged(false);
            painelChat.setVisible(true);
            painelChat.setManaged(true);
        });
        conexao.enviar("JOIN|" + sala);
    }

    @FXML
    private void aoClicarInicio() {
        listaSalas.getSelectionModel().clearSelection();
        salaAtual = null;
        Platform.runLater(() -> {
            listaMensagens.getItems().clear();
            painelChat.setVisible(false);
            painelChat.setManaged(false);
            painelBemVindo.setVisible(true);
            painelBemVindo.setManaged(true);
        });
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
            case "ONLINE" -> {
                if (partes.length >= 3) {
                    Platform.runLater(() -> {
                        labelTotalOnline.setText("● " + partes[1] + " online");
                        contagemPorSala.clear();
                        for (String par : partes[2].split(",")) {
                            String[] kv = par.split(":");
                            if (kv.length == 2) {
                                try { contagemPorSala.put(kv[0], Integer.parseInt(kv[1])); }
                                catch (NumberFormatException ignorado) {}
                            }
                        }
                        listaSalas.refresh();
                    });
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
