package de.caluga.morpheus.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Terminal utility functions
 */
public class TerminalUtils {

    public static class Size {
        private int col, row;

        public Size(int col, int row) {
            this.col = col;
            this.row = row;
        }

        public int getCol() {
            return col;
        }

        public void setCol(int col) {
            this.col = col;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        @Override
        public String toString() {
            return "col=" + col + ", row=" + row;
        }
    }

    public static Size getTerminalSize() {
        return getTerminalSize(false);
    }

    public static Size getTerminalSize(boolean verbose) {
        // Method 1: Try environment variables (set by many terminals)
        try {
            String cols = System.getenv("COLUMNS");
            String rows = System.getenv("LINES");
            if (cols != null && rows != null) {
                int c = Integer.parseInt(cols);
                int r = Integer.parseInt(rows);
                if (verbose) System.err.println("[TerminalUtils] Using COLUMNS/LINES env vars: " + c + "x" + r);
                return new Size(c, r);
            }
        } catch (Exception e) {
            if (verbose) System.err.println("[TerminalUtils] Env vars failed: " + e.getMessage());
        }

        // Method 2: Try stty size (works better with redirected streams)
        try {
            ProcessBuilder pb = new ProcessBuilder("stty", "size");
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();

            if (line != null && !line.isEmpty()) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    int rows = Integer.parseInt(parts[0]);
                    int cols = Integer.parseInt(parts[1]);
                    if (verbose) System.err.println("[TerminalUtils] Using stty size: " + cols + "x" + rows);
                    return new Size(cols, rows);
                }
            }
        } catch (Exception e) {
            if (verbose) System.err.println("[TerminalUtils] stty failed: " + e.getMessage());
        }

        // Method 3: Try tput (fallback)
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "tput cols 2>/dev/null && tput lines 2>/dev/null");
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String colsLine = reader.readLine();
            String rowsLine = reader.readLine();
            process.waitFor();

            if (colsLine != null && rowsLine != null) {
                int cols = Integer.parseInt(colsLine.trim());
                int rows = Integer.parseInt(rowsLine.trim());
                if (verbose) System.err.println("[TerminalUtils] Using tput: " + cols + "x" + rows);
                return new Size(cols, rows);
            }
        } catch (Exception e) {
            if (verbose) System.err.println("[TerminalUtils] tput failed: " + e.getMessage());
        }

        // Fallback: Use default size
        if (verbose) System.err.println("[TerminalUtils] All detection methods failed, using default 80x24");
        return new Size(80, 24);
    }

    public static void moveCursor(int row, int col) {
        System.out.print("\u001B[" + row + ";" + col + "H");
    }

    public static void clearScreen() {
        System.out.print("\u001B[2J");
        System.out.print("\u001B[H");
    }
}
