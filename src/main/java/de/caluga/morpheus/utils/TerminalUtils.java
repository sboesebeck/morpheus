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
        // Method 1: Use stty directly with the controlling terminal
        // This reads the actual kernel terminal size, not cached values
        try {
            // Open a new bash shell that queries the terminal size from its controlling terminal
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", "stty size");
            // Important: inherit the environment so the terminal settings are available
            pb.inheritIO();
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode == 0 && line != null && !line.isEmpty()) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    int rows = Integer.parseInt(parts[0]);
                    int cols = Integer.parseInt(parts[1]);
                    if (verbose) System.err.println("[TerminalUtils] Using stty: " + cols + "x" + rows);
                    return new Size(cols, rows);
                }
            }
        } catch (Exception e) {
            if (verbose) System.err.println("[TerminalUtils] stty failed: " + e.getMessage());
        }

        // Method 2: Try environment variables (set by many terminals)
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

        // Method 3: Try stty size
        try {
            Process process = new ProcessBuilder("sh", "-c", "stty size 2>/dev/null").start();
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
