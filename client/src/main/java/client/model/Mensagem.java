package client.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Mensagem {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm");

    private final String remetente;
    private final String texto;
    private final String hora;

    public Mensagem(String remetente, String texto) {
        this.remetente = remetente;
        this.texto     = texto;
        this.hora      = LocalTime.now().format(FORMATO);
    }

    public String getRemetente() { return remetente; }
    public String getTexto()     { return texto; }

    @Override
    public String toString() {
        return "[" + hora + "] " + remetente + ": " + texto;
    }
}
