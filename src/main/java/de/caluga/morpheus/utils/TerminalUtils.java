package de.caluga.morpheus.utils;

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
        try {
            String[] cmd = {"/bin/sh", "-c", "tput cols && tput lines"};
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();

            byte[] input = new byte[32];
            int size = process.getInputStream().read(input);
            String[] output = new String(input, 0, size).split("\\s+");
            int cols = Integer.parseInt(output[0]);
            int rows = Integer.parseInt(output[1]);

            return new Size(cols, rows);
        } catch (Exception e) {
            // Return default size if detection fails
            return new Size(80, 24);
        }
    }

    public static void moveCursor(int row, int col) {
        System.out.print("\u001B[" + row + ";" + col + "H");
    }

    public static void clearScreen() {
        System.out.print("\u001B[2J");
        System.out.print("\u001B[H");
    }
}
