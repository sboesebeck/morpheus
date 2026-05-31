package de.caluga.morpheus.tui.widget;

import java.util.ArrayList;
import java.util.List;

/** Framework-free single-selection list model. */
public class ListBox<T> {
    private List<T> items;
    private int index;

    public ListBox(List<T> items) {
        setItems(items);
    }

    public void setItems(List<T> items) {
        this.items = new ArrayList<>(items);
        this.index = this.items.isEmpty() ? -1 : 0;
    }

    public void up()   { if (index > 0) index--; }
    public void down() { if (index >= 0 && index < items.size() - 1) index++; }

    public T selected() {
        return index < 0 ? null : items.get(index);
    }

    public int selectedIndex() { return index; }
    public List<T> items()     { return items; }
}
