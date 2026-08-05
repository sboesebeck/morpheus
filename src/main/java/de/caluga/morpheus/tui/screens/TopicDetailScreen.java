package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import static de.caluga.morpheus.tui.TuiText.shortId;
import static de.caluga.morpheus.tui.TuiText.trunc;

import java.util.List;

/** The listener nodes subscribed to one topic (id + host), scrollable. Pure render, no backend. */
public class TopicDetailScreen extends ScrollableDetailScreen {

    public record Listener(String id, String host) {}

    private final String topic;
    private final List<Listener> listeners;

    public TopicDetailScreen(String topic, List<Listener> listeners) {
        this.topic = topic;
        this.listeners = listeners;
    }

    String topic() { return topic; }                 // test seam
    List<Listener> listeners() { return listeners; } // test seam

    @Override
    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();

        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, trunc("Topic: " + topic + "   (" + listeners.size() + " Listener)", width - 3));

        if (listeners.isEmpty()) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, 2, "(keine Listener)");
        } else {
            int visible = Math.max(1, height - 3);
            int maxScroll = Math.max(0, listeners.size() - visible);
            if (scroll > maxScroll) scroll = maxScroll;
            int row = 2;
            for (int i = scroll; i < listeners.size() && row < height - 1; i++) {
                Listener l = listeners.get(i);
                String host = l.host() != null && !l.host().isEmpty() ? "   " + l.host() : "";
                g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
                g.putString(2, row++, trunc(shortId(l.id()) + host, width - 3));
            }
        }

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, "[↑↓] scroll   [esc] zurück   [q] quit");
    }
}
