package de.caluga.morpheus.tui.widget;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ListBoxTest {
    @Test
    void startsAtFirst() {
        ListBox<String> b = new ListBox<>(List.of("a", "b", "c"));
        assertEquals("a", b.selected());
        assertEquals(0, b.selectedIndex());
    }

    @Test
    void downMovesAndClampsAtEnd() {
        ListBox<String> b = new ListBox<>(List.of("a", "b"));
        b.down();
        assertEquals("b", b.selected());
        b.down();
        assertEquals("b", b.selected(), "must clamp at the last item");
    }

    @Test
    void upClampsAtStart() {
        ListBox<String> b = new ListBox<>(List.of("a", "b"));
        b.up();
        assertEquals("a", b.selected());
    }

    @Test
    void emptyListHasNullSelection() {
        ListBox<String> b = new ListBox<>(List.of());
        assertNull(b.selected());
        assertEquals(-1, b.selectedIndex());
        b.down();
        assertNull(b.selected());
    }

    @Test
    void setItemsResetsSelection() {
        ListBox<String> b = new ListBox<>(List.of("a", "b"));
        b.down();
        b.setItems(List.of("x", "y", "z"));
        assertEquals("x", b.selected());
        assertEquals(0, b.selectedIndex());
    }
}
