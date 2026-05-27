package server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rastreia quais usuários estão conectados no momento.
 * Mantido em memória — não precisa sobreviver a reinicializações do servidor.
 */
public class SessionRegistry {

    private static final Set<String> online = ConcurrentHashMap.newKeySet();

    /** @return true se registrado, false se o username já está online */
    public static boolean tryRegister(String username) {
        return online.add(username);
    }

    public static void unregister(String username) {
        if (username != null) online.remove(username);
    }
}
