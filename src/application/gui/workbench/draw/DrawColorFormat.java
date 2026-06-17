package application.gui.workbench.draw;

import java.awt.Color;

/**
 * Color formatting and parsing helpers for the Draw rail.
 */
final class DrawColorFormat {

    private DrawColorFormat() {
    }

    /**
     * Returns an opaque version of a color.
     *
     * @param color source color
     * @return opaque color
     */
    static Color opaque(Color color) {
        return color == null ? Color.GREEN : new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Returns a #RRGGBB label for a color.
     *
     * @param color color
     * @return color label
     */
    static String colorLabel(Color color) {
        Color value = opaque(color);
        return "#" + String.format("%02X%02X%02X", value.getRed(), value.getGreen(), value.getBlue());
    }

    /**
     * Returns a hex label for a color.
     *
     * @param color color
     * @param includeAlpha true to include alpha as AARRGGBB
     * @return normalized hex label
     */
    static String hexLabel(Color color, boolean includeAlpha) {
        Color value = color == null ? Color.GREEN : color;
        if (includeAlpha) {
            return "#" + String.format("%02X%02X%02X%02X",
                    value.getAlpha(), value.getRed(), value.getGreen(), value.getBlue());
        }
        return colorLabel(value);
    }

    /**
     * Parses #RGB, #ARGB, #RRGGBB, #AARRGGBB, or 0x-prefixed variants.
     *
     * @param raw raw field text
     * @param fallbackAlpha alpha used when the text omits one
     * @return parsed color, or null when invalid
     */
    static Color parseHexColor(String raw, int fallbackAlpha) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        if (text.length() == 3 || text.length() == 4) {
            return parseShortHexColor(text, fallbackAlpha);
        }
        if ((text.length() != 6 && text.length() != 8) || !text.matches("[0-9A-Fa-f]+")) {
            return null;
        }
        long parsed;
        try {
            parsed = Long.parseLong(text, 16);
        } catch (NumberFormatException ex) {
            return null;
        }
        int alpha = text.length() == 8 ? (int) ((parsed >> 24) & 0xFF) : clampByte(fallbackAlpha);
        int red = (int) ((parsed >> 16) & 0xFF);
        int green = (int) ((parsed >> 8) & 0xFF);
        int blue = (int) (parsed & 0xFF);
        return new Color(red, green, blue, alpha);
    }

    /**
     * Parses A,R,G,B integer components.
     *
     * @param raw raw field text
     * @return parsed color, or null when invalid
     */
    static Color parseArgbColor(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.regionMatches(true, 0, "argb(", 0, 5) && text.endsWith(")")) {
            text = text.substring(5, text.length() - 1);
        }
        String[] parts = text.split("[,;/\\s]+");
        if (parts.length != 4) {
            return null;
        }
        int[] channels = new int[4];
        for (int i = 0; i < channels.length; i++) {
            try {
                channels[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (channels[i] < 0 || channels[i] > 255) {
                return null;
            }
        }
        return new Color(channels[1], channels[2], channels[3], channels[0]);
    }

    private static Color parseShortHexColor(String text, int fallbackAlpha) {
        int offset = text.length() == 4 ? 1 : 0;
        int alpha = offset == 1 ? hexNibble(text.charAt(0)) * 17 : clampByte(fallbackAlpha);
        int red = hexNibble(text.charAt(offset)) * 17;
        int green = hexNibble(text.charAt(offset + 1)) * 17;
        int blue = hexNibble(text.charAt(offset + 2)) * 17;
        if (alpha < 0 || red < 0 || green < 0 || blue < 0) {
            return null;
        }
        return new Color(red, green, blue, alpha);
    }

    private static int hexNibble(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        return -1;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
