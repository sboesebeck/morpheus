package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.core.MessageInfo;
import de.caluga.morpheus.tui.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Scrollable snapshot of one observed message. It remains valid after the database document is deleted. */
public class MessageDetailScreen implements Screen {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private record Snapshot(
            String id,
            String topic,
            String sender,
            String senderHost,
            String recipient,
            long timestamp,
            int size,
            boolean v5,
            boolean exclusive,
            int processedByCount,
            int updateCount,
            String lockedBy,
            Long lockedUntil,
            String answeredBy,
            String answeredByHost,
            Long rtt,
            boolean timedOut,
            boolean deleted,
            boolean deleteAfterProcessing,
            int deleteAfterProcessingTime,
            String body) {}

    private final Snapshot message;
    private int scroll = 0;
    private int visibleRows = 1;

    public MessageDetailScreen(MessageInfo info) {
        this.message = new Snapshot(
                text(info.id), text(info.topic), text(info.sender), text(info.senderHost), text(info.recipient),
                info.timestamp, info.size, info.isV5, info.isExclusive, info.processedByCount, info.updateCount,
                text(info.lockedBy), info.lockedUntil, text(info.answeredBy), text(info.answeredByHost), info.rtt,
                info.isTimedOut, info.isDeleted, info.isDeleteAfterProcessing, info.deleteAfterProcessingTime,
                text(info.body));
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        if (key.getKeyType() == KeyType.ArrowDown) { scroll++; return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowUp) { scroll = Math.max(0, scroll - 1); return Result.stay(); }
        if (key.getKeyType() == KeyType.PageDown) { scroll += visibleRows; return Result.stay(); }
        if (key.getKeyType() == KeyType.PageUp) { scroll = Math.max(0, scroll - visibleRows); return Result.stay(); }
        if (key.getKeyType() == KeyType.Home) { scroll = 0; return Result.stay(); }
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        List<String> lines = buildLines(width);
        visibleRows = Math.max(1, height - 1);
        scroll = Math.min(scroll, Math.max(0, lines.size() - visibleRows));

        for (int i = 0; i < visibleRows && scroll + i < lines.size(); i++) {
            String line = lines.get(scroll + i);
            TextColor color = line.startsWith("Nachricht ") ? TextColor.ANSI.CYAN_BRIGHT
                    : line.startsWith("── ") ? TextColor.ANSI.YELLOW
                    : line.startsWith("  Status") && (message.deleted || message.timedOut)
                    ? TextColor.ANSI.RED_BRIGHT : TextColor.ANSI.DEFAULT;
            g.setForegroundColor(color);
            g.putString(2, i, trunc(line, width - 3));
        }

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, trunc("[↑↓/PgUp/Dn/Home] scrollen  [esc] zurück  [q] quit", width - 3));
    }

    List<String> buildLines(int width) {
        List<String> lines = new ArrayList<>();
        lines.add("Nachricht " + value(message.topic));

        section(lines, "Identität");
        kv(lines, "ID", message.id);
        kv(lines, "Topic", message.topic);
        kv(lines, "Protokoll", message.v5 ? "V5" : "V6");
        kv(lines, "Zeit", TIME_FORMAT.format(Instant.ofEpochMilli(message.timestamp)));
        kv(lines, "Größe (ca.)", message.size + " B");

        section(lines, "Routing / Verarbeitung");
        kv(lines, "Sender", endpoint(message.sender, message.senderHost));
        kv(lines, "Empfänger", message.recipient);
        kv(lines, "Exklusiv", yesNo(message.exclusive));
        kv(lines, "Processed by", String.valueOf(message.processedByCount));
        kv(lines, "Updates", String.valueOf(message.updateCount));
        kv(lines, "Status", status());
        if (!message.lockedBy.isEmpty()) kv(lines, "Lock", lock());
        kv(lines, "Delete after proc.", message.deleteAfterProcessing
                ? "ja, nach " + message.deleteAfterProcessingTime + " ms" : "nein");

        section(lines, "Antwort");
        kv(lines, "Antwort von", endpoint(message.answeredBy, message.answeredByHost));
        kv(lines, "RTT", message.rtt == null ? "—" : message.rtt + " ms");

        section(lines, "Payload-Snapshot (max. 2048 Zeichen)");
        if (message.body.isEmpty()) {
            lines.add("  (leer)");
        } else {
            wrap(lines, message.body, Math.max(10, width - 5));
        }
        return lines;
    }

    String messageId() { return message.id; }

    String body() { return message.body; }

    private String status() {
        List<String> states = new ArrayList<>();
        if (message.deleted) states.add("gelöscht (lokaler Snapshot)");
        if (message.timedOut) states.add("Timeout");
        if (message.rtt != null) states.add("beantwortet");
        if (message.processedByCount > 0) states.add("verarbeitet");
        return states.isEmpty() ? "offen" : String.join(", ", states);
    }

    private String lock() {
        if (message.lockedUntil == null) return message.lockedBy;
        return message.lockedBy + " bis " + TIME_FORMAT.format(Instant.ofEpochMilli(message.lockedUntil));
    }

    private static String endpoint(String id, String host) {
        if (id.isEmpty()) return "—";
        return host.isEmpty() ? id : id + " @ " + host;
    }

    private static String yesNo(boolean value) { return value ? "ja" : "nein"; }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }

    private static String value(String value) { return value.isEmpty() ? "—" : value; }

    private static void section(List<String> lines, String title) {
        lines.add("");
        lines.add("── " + title + " ──");
    }

    private static void kv(List<String> lines, String label, String value) {
        lines.add(String.format("  %-20s %s", label, value(value)));
    }

    private static void wrap(List<String> lines, String text, int width) {
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("  ");
                continue;
            }
            int offset = 0;
            while (offset < paragraph.length()) {
                int end = Math.min(paragraph.length(), offset + width);
                lines.add("  " + paragraph.substring(offset, end));
                offset = end;
            }
        }
    }

    private static String trunc(String value, int max) {
        if (value == null || max <= 1) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
