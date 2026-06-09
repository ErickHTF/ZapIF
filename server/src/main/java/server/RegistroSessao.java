package server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RegistroSessao {

    private static final Set<String> online = ConcurrentHashMap.newKeySet();

    public static boolean tentarRegistrar(String nomeUsuario) {
        return online.add(nomeUsuario);
    }

    public static void desregistrar(String nomeUsuario) {
        if (nomeUsuario != null) online.remove(nomeUsuario);
    }

    public static int totalOnline() {
        return online.size();
    }
}
