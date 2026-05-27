module client {
    requires javafx.controls;
    requires javafx.fxml;
    opens client    to javafx.fxml;
    opens client.ui to javafx.fxml;
    exports client;
}
