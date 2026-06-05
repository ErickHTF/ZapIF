package server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Sala {
    private final String nome;
    private final CopyOnWriteArrayList<ManipuladorCliente> clientes = new CopyOnWriteArrayList<>();
    private static final Map<String, Sala> salas = new ConcurrentHashMap<>();

    private Sala(String nome) {
        this.nome = nome;
    }

    public static Sala obterOuCriar(String nome) {
        return salas.computeIfAbsent(nome, Sala::new);
    }

    public void adicionarCliente(ManipuladorCliente cliente) { clientes.add(cliente); }
    public void removerCliente(ManipuladorCliente cliente)   { clientes.remove(cliente); }

    public void transmitir(String mensagem, ManipuladorCliente excluir) {
        for (ManipuladorCliente cliente : clientes) {
            if (cliente != excluir) cliente.enviarMensagem(mensagem);
        }
    }

    public String getNome()   { return nome; }
    public int    getContagem() { return clientes.size(); }

    public static String construirContagens() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Sala> e : salas.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue().getContagem());
        }
        return sb.toString();
    }
}
