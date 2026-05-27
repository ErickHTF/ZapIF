package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    static final int PORT        = 5000;
    static final int MAX_CLIENTS = 100;

    public static void main(String[] args) {
        Database.init();

        ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTS);

        // Chamado pelo SO no Ctrl+C / SIGTERM — dá tempo aos handlers de rodar cleanup()
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Encerrando servidor...");
            pool.shutdown();
            try { pool.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }));

        System.out.println("Servidor iniciado na porta " + PORT + " (max " + MAX_CLIENTS + " clientes)");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente: " + clientSocket.getInetAddress());
                pool.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            if (!pool.isShutdown()) {
                System.err.println("Erro fatal no servidor: " + e.getMessage());
            }
        }
    }
}
