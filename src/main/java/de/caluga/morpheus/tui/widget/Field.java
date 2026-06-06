package de.caluga.morpheus.tui.widget;

/** A single editable form field (plain text or masked password). */
public class Field {
    public enum Type { TEXT, PASSWORD }

    private final String label;
    private final StringBuilder value;
    private final Type type;

    public Field(String label, String value, Type type) {
        this.label = label;
        this.value = new StringBuilder(value == null ? "" : value);
        this.type = type;
    }

    public void type(char c)    { value.append(c); }
    public void backspace()     { if (value.length() > 0) value.setLength(value.length() - 1); }
    public String value()       { return value.toString(); }
    public String label()       { return label; }
    public Type fieldType()     { return type; }

    public String display() {
        return type == Type.PASSWORD ? "•".repeat(value.length()) : value.toString();
    }
}
