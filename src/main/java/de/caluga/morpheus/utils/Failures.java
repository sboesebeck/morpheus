package de.caluga.morpheus.utils;

/**
 * Renders a {@link Throwable}'s cause chain as a short suffix, so any UI that only ever shows
 * one string - a TUI status line, an exception's own {@code getMessage()} rendered without a
 * stack trace - still surfaces a wrapped cause (e.g. an {@code SSLHandshakeException} from an
 * untrusted certificate) instead of a bare, unhelpful headline like "Failed to connect after
 * 10 attempts". Used by {@code MorphiumConnectionFactory}, {@code ConnectingScreen} and
 * {@code ConnectionTester} - all three would otherwise report a connect failure with none of
 * the diagnostic detail the underlying driver actually captured.
 */
public final class Failures {
    private Failures() {
    }

    /** {@code "ClassName: message"} for one throwable, or just {@code "ClassName"} if the message is blank. */
    public static String describe(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null || msg.isBlank() ? "" : ": " + msg);
    }

    /**
     * {@code " - <describe(cause)> (root cause: <describe(root)>)"}, or {@code ""} if
     * {@code cause} is {@code null}. The root-cause parenthetical is omitted when the cause
     * chain is only one level deep (root == cause already).
     */
    public static String causeDetail(Throwable cause) {
        if (cause == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" - ").append(describe(cause));
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != cause) {
            sb.append(" (root cause: ").append(describe(root)).append(")");
        }
        return sb.toString();
    }
}
