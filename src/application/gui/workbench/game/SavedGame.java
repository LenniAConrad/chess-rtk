package application.gui.workbench.game;

import utility.Json;

/**
 * One locally saved Workbench game line.
 *
 * @param id stable local identifier
 * @param createdAtMillis creation timestamp
 * @param updatedAtMillis last update timestamp
 * @param title display title
 * @param status compact state label
 * @param currentPly selected ply to reopen
 * @param startFen root FEN
 * @param currentFen current/final FEN
 * @param uciLine mainline UCI moves
 * @param sanLine mainline SAN moves
 * @param pgn PGN export
 */
public record SavedGame(
        String id,
        long createdAtMillis,
        long updatedAtMillis,
        String title,
        String status,
        int currentPly,
        String startFen,
        String currentFen,
        String uciLine,
        String sanLine,
        String pgn) {

    /**
     * Captures a saved-game snapshot from the current game model.
     *
     * @param id stable saved-game id
     * @param createdAtMillis creation timestamp to preserve
     * @param updatedAtMillis update timestamp
     * @param status state label
     * @param model source model
     * @return saved game snapshot
     */
    public static SavedGame capture(String id, long createdAtMillis, long updatedAtMillis,
            String status, GameModel model) {
        String san = model.sanLine();
        String uci = model.uciLine();
        String start = model.startPosition().toString();
        String current = model.currentPosition().toString();
        return new SavedGame(clean(id), createdAtMillis, updatedAtMillis, titleFromSan(san, current),
                clean(status).isBlank() ? "Saved" : clean(status), model.currentPly(),
                start, current, uci, san, model.pgn());
    }

    /**
     * Returns the number of half-moves in the saved line.
     *
     * @return ply count
     */
    public int plyCount() {
        String moves = clean(uciLine);
        return moves.isBlank() ? 0 : moves.split("\\s+").length;
    }

    /**
     * Serializes this saved game as one JSONL object.
     *
     * @return JSON object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(384 + clean(pgn).length());
        sb.append('{');
        stringField(sb, "schemaVersion", "workbench.savedGame.v1", false);
        stringField(sb, "id", id, true);
        numberField(sb, "createdAtMillis", createdAtMillis);
        numberField(sb, "updatedAtMillis", updatedAtMillis);
        stringField(sb, "title", title, true);
        stringField(sb, "status", status, true);
        numberField(sb, "currentPly", currentPly);
        stringField(sb, "startFen", startFen, true);
        stringField(sb, "currentFen", currentFen, true);
        stringField(sb, "uciLine", uciLine, true);
        stringField(sb, "sanLine", sanLine, true);
        stringField(sb, "pgn", pgn, true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses one saved-game JSONL row.
     *
     * @param json JSON object
     * @return parsed saved game, or null when required fields are missing
     */
    public static SavedGame fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String id = Json.parseStringField(json, "id");
        String startFen = Json.parseStringField(json, "startFen");
        String uciLine = Json.parseStringField(json, "uciLine");
        if (id == null || startFen == null || uciLine == null) {
            return null;
        }
        long created = parseLong(json, "createdAtMillis", 0L);
        long updated = parseLong(json, "updatedAtMillis", created);
        String currentFen = Json.parseStringField(json, "currentFen");
        String sanLine = Json.parseStringField(json, "sanLine");
        String pgn = Json.parseStringField(json, "pgn");
        String title = Json.parseStringField(json, "title");
        String status = Json.parseStringField(json, "status");
        int currentPly = (int) parseLong(json, "currentPly", countPly(uciLine));
        return new SavedGame(id, created, updated,
                clean(title).isBlank() ? titleFromSan(sanLine, currentFen) : title,
                clean(status).isBlank() ? "Saved" : status,
                currentPly,
                startFen,
                clean(currentFen).isBlank() ? startFen : currentFen,
                uciLine,
                clean(sanLine),
                clean(pgn));
    }

    /**
     * Returns the title from SAN.
     *
     * @param san SAN move string
     * @param fallbackFen FEN string for fallback
     * @return title from SAN text
     */
    private static String titleFromSan(String san, String fallbackFen) {
        String text = clean(san);
        if (text.isBlank()) {
            return "Position " + compactFen(fallbackFen);
        }
        return text.length() <= 56 ? text : text.substring(0, 53) + "...";
    }

    /**
     * Returns the compact FEN.
     *
     * @param fen FEN string
     * @return compact FEN text
     */
    private static String compactFen(String fen) {
        String text = clean(fen);
        int firstSpace = text.indexOf(' ');
        String board = firstSpace < 0 ? text : text.substring(0, firstSpace);
        return board.length() <= 24 ? board : board.substring(0, 21) + "...";
    }

    /**
     * Appends a JSON string field, cleaning null values and escaping content.
     *
     * @param sb destination JSON builder
     * @param name field name
     * @param value raw field value
     * @param comma whether to prepend a comma
     */
    private static void stringField(StringBuilder sb, String name, String value, boolean comma) {
        if (comma) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":\"").append(Json.esc(clean(value))).append('"');
    }

    /**
     * Appends a JSON numeric field.
     *
     * @param sb destination JSON builder
     * @param name field name
     * @param value numeric value
     */
    private static void numberField(StringBuilder sb, String name, long value) {
        sb.append(',').append('"').append(name).append("\":").append(value);
    }

    /**
     * Parses the parse long.
     *
     * @param json JSON payload or flag
     * @param name display name
     * @param fallback default used when input is absent or invalid
     * @return parse long
     */
    private static long parseLong(String json, String name, long fallback) {
        try {
            return Json.parseLongField(json, name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /**
     * Returns the count ply.
     *
     * @param uciLine source uci line
     * @return count ply
     */
    private static int countPly(String uciLine) {
        String text = clean(uciLine);
        return text.isBlank() ? 0 : text.split("\\s+").length;
    }

    /**
     * Normalizes nullable persisted text to a trimmed non-null value.
     *
     * @param text persisted text, or {@code null}
     * @return trimmed text, or an empty string
     */
    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
