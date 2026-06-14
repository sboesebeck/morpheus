package de.caluga.morpheus.tui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TopicPaletteTest {

    @Test
    void stableAndNonNull() {
        assertSame(TopicPalette.colorFor("order.created"), TopicPalette.colorFor("order.created"));
        assertNotNull(TopicPalette.colorFor("invoice.req"));
        assertNotNull(TopicPalette.colorFor(null));
    }
}
