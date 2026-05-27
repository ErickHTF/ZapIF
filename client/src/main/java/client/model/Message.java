package client.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final String sender;
    private final String text;
    private final String time;

    public Message(String sender, String text) {
        this.sender = sender;
        this.text   = text;
        this.time   = LocalTime.now().format(FMT);
    }

    public String getSender() { return sender; }
    public String getText()   { return text; }

    @Override
    public String toString() {
        return "[" + time + "] " + sender + ": " + text;
    }
}
