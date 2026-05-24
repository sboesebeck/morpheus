package de.caluga.morpheus.rendering;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import de.caluga.morpheus.config.ConfigurationManager;

/**
 * Manages ANSI color codes, themes, and text rendering
 */
public class ThemeManager {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BG_BLACK = "\u001B[40m";
    private static final String ANSI_BG_RED = "\u001B[41m";
    private static final String ANSI_BG_GREEN = "\u001B[42m";
    private static final String ANSI_BG_BLUE = "\u001B[44m";
    private static final String ANSI_BG_YELLOW = "\u001B[43m";
    private static final String ANSI_BG_MAGENTA = "\u001B[45m";
    private static final String ANSI_BG_CYAN = "\u001B[46m";
    private static final String ANSI_BG_WHITE = "\u001B[47m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_FAINT = "\u001B[2m";
    private static final String ANSI_ITALIC = "\u001B[3m";
    private static final String ANSI_UNDERLINE = "\u001B[4m";
    private static final String ANSI_SLOW_BLINK = "\u001B[5m";
    private static final String ANSI_FAST_BLINK = "\u001B[6m";
    private static final String ANSI_INVERT = "\u001B[7m";
    private static final String ANSI_HIDDEN = "\u001B[8m";
    private static final String ANSI_STRIKETHROUGH = "\u001B[9m";

    private final Map<String, String> ansiCodes = new HashMap<>();
    private final String themeName;
    private final Properties properties;

    public enum Gradient {
        blue, cyan, grey, green, red, yellow, purple
    }

    public ThemeManager(ConfigurationManager config) {
        this.themeName = config.getTheme();
        this.properties = config.getProperties();

        registerAnsiCodes();
        loadThemeSettings();
    }

    private void registerAnsiCodes() {
        ansiCodes.put("r", ANSI_RESET);
        ansiCodes.put("rd", ANSI_RED);
        ansiCodes.put("gr", ANSI_GREEN);
        ansiCodes.put("y", ANSI_YELLOW);
        ansiCodes.put("b", ANSI_BLUE);
        ansiCodes.put("bl", ANSI_BLACK);
        ansiCodes.put("m", ANSI_MAGENTA);
        ansiCodes.put("c", ANSI_CYAN);
        ansiCodes.put("w", ANSI_WHITE);
        ansiCodes.put("b_bl", ANSI_BG_BLACK);
        ansiCodes.put("b_rd", ANSI_BG_RED);
        ansiCodes.put("b_gr", ANSI_BG_GREEN);
        ansiCodes.put("b_y", ANSI_BG_YELLOW);
        ansiCodes.put("b_b", ANSI_BG_BLUE);
        ansiCodes.put("b_m", ANSI_BG_MAGENTA);
        ansiCodes.put("b_c", ANSI_BG_CYAN);
        ansiCodes.put("b_w", ANSI_BG_WHITE);
        ansiCodes.put("ital", ANSI_ITALIC);
        ansiCodes.put("bld", ANSI_BOLD);
        ansiCodes.put("fnt", ANSI_FAINT);
        ansiCodes.put("sb", ANSI_SLOW_BLINK);
        ansiCodes.put("fb", ANSI_FAST_BLINK);
        ansiCodes.put("inv", ANSI_INVERT);
        ansiCodes.put("hid", ANSI_HIDDEN);
        ansiCodes.put("str", ANSI_STRIKETHROUGH);
        ansiCodes.put("ul", ANSI_UNDERLINE);
    }

    private void loadThemeSettings() {
        for (Object key : properties.keySet()) {
            if (key.toString().startsWith("theme." + themeName)) {
                String themeKey = key.toString().split("\\.")[2];
                ansiCodes.put(themeKey, processAnsiString(properties.getProperty(key.toString())));
            }
        }
    }

    public String getAnsiFGColor(int colNum) {
        return "\u001B[38;5;" + colNum + "m";
    }

    public String getAnsiBGColor(int colNum) {
        return "\u001B[48;5;" + colNum + "m";
    }

    public String ansiReset() {
        return ANSI_RESET;
    }

    private String processAnsiString(String str) {
        String out = str;
        for (String key : ansiCodes.keySet()) {
            out = out.replaceAll("\\[" + key + "\\]", ansiCodes.get(key));
        }
        for (int i = 0; i < 255; i++) {
            out = out.replaceAll("\\[fg" + i + "\\]", getAnsiFGColor(i));
            out = out.replaceAll("\\[bg" + i + "\\]", getAnsiBGColor(i));
        }
        return out;
    }

    public String render(String str) {
        return processAnsiString(str);
    }

    public void print(String str) {
        System.out.println(render(str));
    }

    public void print(String str, int themeGradientNr) {
        String gradient = properties.getProperty("theme." + themeName + ".gradient" + themeGradientNr, "grey");
        print(str, Gradient.valueOf(gradient));
    }

    public void print(String str, Gradient gr) {
        int[] gradient;

        switch (gr) {
            case yellow:
                gradient = new int[] {184, 220, 226, 227, 228, 229, 230, 231};
                break;
            case cyan:
                gradient = new int[] {29, 41, 42, 43, 48, 49, 50, 51};
                break;
            case blue:
                gradient = new int[] {25, 26, 27, 32, 33, 38, 39};
                break;
            case green:
                gradient = new int[] {22, 28, 34, 40, 46, 118, 119, 120};
                break;
            case red:
                gradient = new int[] {88, 124, 125, 160, 196};
                break;
            case purple:
                gradient = new int[] {53, 91, 127, 163, 199};
                break;
            case grey:
            default:
                gradient = new int[] {241, 243, 245, 248, 249, 252, 254, 231};
        }

        int l = str.length();
        int chk = l / (gradient.length * 2);
        if (chk == 0) chk = 1;

        int gridx = 0;
        boolean flag = true;
        int off = 1;

        for (int idx = 0; idx < l; idx++) {
            if (flag) {
                System.out.print(getAnsiFGColor(gradient[gridx]));
                gridx += off;

                if (gridx >= gradient.length - 1) {
                    off = -off;
                    gridx = gradient.length - 1;
                } else if (gridx <= 0) {
                    gridx = 0;
                }
                flag = false;
            }

            if (idx != 0 && idx % chk == 0) {
                flag = true;
            }

            System.out.print(str.charAt(idx));
        }

        System.out.println(ANSI_RESET);
    }

    public String getColumn(String str, int len) {
        if (str == null) return "null";
        if (str.length() >= len) {
            return str.substring(0, len);
        }

        int ctr = (len - str.length()) / 2;
        for (int i = 0; i < ctr; i++) {
            str = " " + str + " ";
        }

        while (str.length() > len) {
            str = str.substring(0, str.length() - 1);
        }

        while (str.length() < len) {
            str = str + " ";
        }

        return str;
    }
}
