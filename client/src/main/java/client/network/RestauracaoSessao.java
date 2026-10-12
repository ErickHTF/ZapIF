package client.network;

import java.util.function.Consumer;

/** Restores authentication before joining the previous room on a new socket. */
public final class RestauracaoSessao {
    private final String nome;
    private final String senha;
    private boolean restaurando;

    public RestauracaoSessao(String nome, String senha) {
        this.nome = nome;
        this.senha = senha;
    }

    public void desconectou() { restaurando = true; }
    public boolean pendente() { return restaurando; }

    public void conectou(Consumer<String> enviar) {
        if (restaurando) enviar.accept("LOGIN|" + nome + "|" + senha);
    }

    public boolean recebeu(String mensagem, String sala, Consumer<String> enviar) {
        if (!restaurando) return false;
        String[] partes = mensagem.split("\\|", -1);
        if ("ROOMS".equals(partes[0])) {
            if (sala == null) restaurando = false;
            else enviar.accept("JOIN|" + sala);
        } else if ("HISTORY".equals(partes[0]) && partes.length >= 2 && partes[1].equals(sala)) {
            restaurando = false;
        }
        return !restaurando;
    }
}
