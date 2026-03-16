package chess.tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TagLine {

    final String raw;
    final String family;
    final Map<String, String> fields;

    TagLine(String raw) {
        this.raw = raw == null ? "" : raw.trim();
        int idx = this.raw.indexOf(':');
        if (idx <= 0) {
            this.family = "";
            this.fields = Map.of();
            return;
        }
        this.family = this.raw.substring(0, idx).trim().toUpperCase();
        String rest = this.raw.substring(idx + 1).trim();
        this.fields = parseFields(rest);
    }

    private static Map<String, String> parseFields(String rest) {
        if (rest == null || rest.isEmpty()) {
            return Map.of();
        }
        List<String> tokens = splitTokens(rest);
        Map<String, String> out = new LinkedHashMap<>();
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq).trim();
            String value = token.substring(eq + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = unescape(value.substring(1, value.length() - 1));
            }
            if (!key.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static List<String> splitTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        boolean escape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                if (inQuote) {
                    escape = true;
                }
                continue;
            }
            if (c == '"') {
                inQuote = !inQuote;
                current.append(c);
                continue;
            }
            if (Character.isWhitespace(c) && !inQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
