package server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sala de chat com sua lista de clientes conectados.
 * O broadcast itera enquanto clientes entram e saem — por isso a lista é thread-safe.
 */
public class Room {
    private final String name;
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();

    private Room(String name) {
        this.name = name;
    }

    // computeIfAbsent é atômico — garante que só uma sala é criada por nome
    public static Room getOrCreate(String name) {
        return rooms.computeIfAbsent(name, Room::new);
    }

    public void addClient(ClientHandler client)    { clients.add(client); }
    public void removeClient(ClientHandler client) { clients.remove(client); }

    /** Envia para todos na sala. Passa null em exclude para incluir o remetente. */
    public void broadcast(String message, ClientHandler exclude) {
        for (ClientHandler client : clients) {
            if (client != exclude) client.sendMessage(message);
        }
    }

    public String getName() { return name; }
}
