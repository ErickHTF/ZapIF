package server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sala de chat com sua lista de clientes conectados.
 * O broadcast itera enquanto clientes entram e saem — por isso a lista é thread-safe.
 */
public class Sala {
    private final String nome;
    private final CopyOnWriteArrayList<ManipuladorCliente> clientes = new CopyOnWriteArrayList<>();
    private static final Map<String, Sala> salas = new ConcurrentHashMap<>();

    private Sala(String nome) {
        this.nome = nome;
    }

    // computeIfAbsent é atômico — garante que só uma sala é criada por nome
    public static Sala obterOuCriar(String nome) {
        return salas.computeIfAbsent(nome, Sala::new);
    }

    public void adicionarCliente(ManipuladorCliente cliente)  { clientes.add(cliente); }
    public void removerCliente(ManipuladorCliente cliente)    { clientes.remove(cliente); }

    /** Envia para todos na sala. Passa null em excluir para incluir o remetente. */
    public void transmitir(String mensagem, ManipuladorCliente excluir) {
        for (ManipuladorCliente cliente : clientes) {
            if (cliente != excluir) cliente.enviarMensagem(mensagem);
        }
    }

    public String getNome() { return nome; }
}
