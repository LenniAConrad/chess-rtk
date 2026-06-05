package chess.describe;

import java.util.List;

import chess.classical.Wdl;

/**
 * Deterministic word-choice helpers for human position descriptions.
 *
 * <p>
 * Every method is a pure function of its arguments: no randomness, no clock, no
 * locale-dependent formatting. This keeps generated prose byte-identical across
 * runs and machines so the CLI training export and golden tests stay stable.
 * </p>
 */
final class DescriptionLexicon {

    /**
     * Cardinal words for zero through twenty.
     */
    private static final String[] ONES = {
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty"
    };

    /**
     * Cardinal words for the tens from twenty through ninety.
     */
    private static final String[] TENS = {
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    /**
     * Prevents instantiation.
     */
    private DescriptionLexicon() {
        // utility
    }

    /**
     * Spells a small non-negative integer as English words.
     *
     * <p>
     * Covers the full legal-move range (0&ndash;218); larger or negative values
     * fall back to digits.
     * </p>
     *
     * @param n value to spell
     * @return English words, or the decimal string when out of range
     */
    static String numberWord(int n) {
        if (n < 0 || n > 999) {
            return Integer.toString(n);
        }
        if (n <= 20) {
            return ONES[n];
        }
        if (n < 100) {
            int tens = n / 10;
            int rest = n % 10;
            return rest == 0 ? TENS[tens] : TENS[tens] + "-" + ONES[rest];
        }
        int hundreds = n / 100;
        int rest = n % 100;
        String head = ONES[hundreds] + " hundred";
        return rest == 0 ? head : head + " " + numberWord(rest);
    }

    /**
     * Formats a centipawn score as a signed pawn figure with one decimal.
     *
     * @param cp centipawns
     * @return signed pawn text such as {@code +1.6} or {@code -1.0}
     */
    static String pawns(int cp) {
        String sign = cp >= 0 ? "+" : "-";
        int abs = Math.abs(cp);
        int whole = abs / 100;
        int tenths = (abs % 100 + 5) / 10;
        if (tenths >= 10) {
            whole += 1;
            tenths -= 10;
        }
        return sign + whole + "." + tenths;
    }

    /**
     * Formats a WDL triple verbatim as {@code win/draw/loss}.
     *
     * @param wdl WDL tuple, or null
     * @return permille text, or {@code -} when absent
     */
    static String wdlTriple(Wdl wdl) {
        if (wdl == null) {
            return "-";
        }
        return wdl.win() + "/" + wdl.draw() + "/" + wdl.loss();
    }

    /**
     * Chooses the English indefinite article for a following word.
     *
     * @param word following word
     * @return {@code a} or {@code an}
     */
    static String article(String word) {
        if (word == null || word.isEmpty()) {
            return "a";
        }
        char first = Character.toLowerCase(word.charAt(0));
        return first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u' ? "an" : "a";
    }

    /**
     * Joins phrases as a natural English list with an {@code and} before the last.
     *
     * @param parts phrases
     * @return joined list, or empty text when no parts
     */
    static String joinList(List<String> parts) {
        int n = parts.size();
        if (n == 0) {
            return "";
        }
        if (n == 1) {
            return parts.get(0);
        }
        if (n == 2) {
            return parts.get(0) + " and " + parts.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts.get(i));
        }
        return sb.append(" and ").append(parts.get(n - 1)).toString();
    }

    /**
     * Capitalizes the first character of a phrase.
     *
     * @param text input text
     * @return text with an upper-case first character
     */
    static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Ensures a fragment ends with terminal punctuation.
     *
     * @param text sentence fragment
     * @return punctuated sentence
     */
    static String sentence(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return trimmed;
        }
        return trimmed + ".";
    }
}
