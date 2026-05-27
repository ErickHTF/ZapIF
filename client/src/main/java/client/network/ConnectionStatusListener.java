package client.network;

public interface ConnectionStatusListener {
    void onStatusChange(Connection.Status status);
}
