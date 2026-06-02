package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Servidor {
    static final int PORTA        = 5001;
    static final int MAX_CLIENTES = 100;

    public static void main(String[] args) {
        BancoDados.inicializar();

        ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTES);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Encerrando servidor...");
            pool.shutdown();
            try { pool.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignorado) {}
        }));

        System.out.println("Servidor iniciado na porta " + PORTA + " (max " + MAX_CLIENTES + " clientes)");

        try (ServerSocket socketServidor = new ServerSocket(PORTA)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket socketCliente = socketServidor.accept();
                System.out.println("Novo cliente: " + socketCliente.getInetAddress());
                pool.submit(new ManipuladorCliente(socketCliente));
            }
        } catch (IOException e) {
            if (!pool.isShutdown()) {
                System.err.println("Erro fatal no servidor: " + e.getMessage());
            }
        }
    }
}
