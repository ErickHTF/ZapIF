package client.network;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free protocol regression test: run with java -ea. */
public class RestauracaoSessaoTest {
    public static void main(String[] args) {
        var enviados = new ArrayList<String>();
        var sessao = new RestauracaoSessao("ana", "secret");
        sessao.conectou(enviados::add);
        assert enviados.isEmpty();
        sessao.desconectou();
        sessao.conectou(enviados::add);
        assert enviados.equals(List.of("LOGIN|ana|secret"));
        assert !sessao.recebeu("OK|LOGIN", "geral", enviados::add);
        assert !sessao.recebeu("ROOMS|geral,ajuda", "geral", enviados::add);
        assert enviados.equals(List.of("LOGIN|ana|secret", "JOIN|geral"));
        assert !sessao.recebeu("HISTORY|ajuda", "geral", enviados::add);
        assert sessao.recebeu("HISTORY|geral|old message", "geral", enviados::add);
        assert !sessao.pendente();
        sessao.desconectou();
        sessao.conectou(enviados::add);
        assert !sessao.recebeu("ERROR|Usuário já conectado", "geral", enviados::add);
        assert sessao.pendente();
        assert sessao.recebeu("ROOMS|geral", null, enviados::add);
        assert !sessao.pendente();
        System.out.println("Session restoration regression checks passed");
    }
}
