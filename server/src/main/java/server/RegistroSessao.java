package server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rastreia quais usuários estão conectados no momento.
 * Mantido em memória — não precisa sobreviver a reinicializações do servidor.
 */
public class RegistroSessao {

    private static final Set<String> online = ConcurrentHashMap.newKeySet();

    /** @return true se registrado, false se o nome de usuário já está online */
    public static boolean tentarRegistrar(String nomeUsuario) {
        return online.add(nomeUsuario);
    }

    public static void desregistrar(String nomeUsuario) {
        if (nomeUsuario != null) online.remove(nomeUsuario);
    }
}
