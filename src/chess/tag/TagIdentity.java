package chess.tag;

final class TagIdentity {

    private TagIdentity() {
        // utility
    }

    static String identity(TagLine line) {
        if (line == null) {
            return "";
        }
        String fam = line.family;
        switch (fam) {
            case "META":
                return "META:" + firstKey(line);
            case "FACT":
                return "FACT:" + firstKey(line);
            case "MATERIAL":
                if (line.fields.containsKey("piece") && line.fields.containsKey("count")
                        && line.fields.containsKey("side")) {
                    return "MATERIAL:piece_count:" + line.fields.get("side") + ":" + line.fields.get("piece");
                }
                return "MATERIAL:" + firstKey(line);
            case "PIECE":
                if (line.fields.containsKey("tier")) {
                    return "PIECE:tier:" + line.fields.get("side") + ":" + line.fields.get("piece") + ":"
                            + line.fields.get("square");
                }
                if (line.fields.containsKey("extreme")) {
                    return "PIECE:extreme:" + line.fields.get("extreme");
                }
                if (line.fields.containsKey("activity")) {
                    return "PIECE:activity:" + line.fields.get("activity") + ":" + line.fields.get("side") + ":"
                            + line.fields.get("piece") + ":" + line.fields.get("square");
                }
                return "PIECE:" + firstKey(line);
            case "PAWN":
                if (line.fields.containsKey("structure")) {
                    String structure = line.fields.get("structure");
                    if ("connected_passed".equals(structure)) {
                        return "PAWN:structure:" + structure + ":" + line.fields.get("side") + ":"
                                + line.fields.get("squares");
                    }
                    if ("doubled".equals(structure)) {
                        return "PAWN:structure:" + structure + ":" + line.fields.get("side") + ":"
                                + line.fields.get("file");
                    }
                    return "PAWN:structure:" + structure + ":" + line.fields.get("side") + ":"
                            + line.fields.get("square");
                }
                if (line.fields.containsKey("islands")) {
                    return "PAWN:islands:" + line.fields.get("side");
                }
                if (line.fields.containsKey("majority")) {
                    return "PAWN:majority:" + line.fields.get("side");
                }
                return "PAWN:" + firstKey(line);
            case "KING":
                return "KING:" + firstKey(line) + ":" + line.fields.get("side");
            case "TACTIC":
                if (line.fields.containsKey("detail")) {
                    return "TACTIC:" + line.fields.get("motif") + ":" + line.fields.get("detail");
                }
                if (line.fields.containsKey("side")) {
                    return "TACTIC:" + line.fields.get("motif") + ":" + line.fields.get("side");
                }
                return "TACTIC:" + line.fields.get("motif");
            case "THREAT":
                return "THREAT:" + line.fields.get("type") + ":" + line.fields.get("side");
            case "SPACE":
            case "INITIATIVE":
            case "DEVELOPMENT":
            case "MOBILITY":
                return fam;
            case "OUTPOST":
                return "OUTPOST:" + line.fields.get("side") + ":" + line.fields.get("square") + ":"
                        + line.fields.get("piece");
            case "ENDGAME":
            case "OPENING":
                return fam + ":" + firstKey(line);
            default:
                return line.raw;
        }
    }

    private static String firstKey(TagLine line) {
        if (line.fields.isEmpty()) {
            return "";
        }
        return line.fields.keySet().iterator().next();
    }
}
