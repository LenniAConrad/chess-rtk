package application.gui.workbench.engine;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed and formatted gauntlet result summary for result cards and charts.
 *
 * @param complete true when a process result has arrived
 * @param wins candidate wins
 * @param draws draws
 * @param losses candidate losses
 * @param games total games
 * @param scorePercent score percentage
 * @param elo estimate text
 * @param errors error summary
 * @param millis elapsed duration
 */
record GauntletResultSummary(
        boolean complete,
        int wins,
        int draws,
        int losses,
        int games,
        Double scorePercent,
        String elo,
        String errors,
        long millis) {

    /**
     * Empty summary.
     *
     * @return summary
     */
    static GauntletResultSummary empty() {
        return new GauntletResultSummary(false, 0, 0, 0, 0, null, "-", "-", 0L);
    }

    /**
     * Running summary.
     *
     * @param games expected games
     * @return summary
     */
    static GauntletResultSummary running(int games) {
        return new GauntletResultSummary(false, 0, 0, 0, games, null, "-", "-", 0L);
    }

    /**
     * Live, in-progress summary built from the running per-game tally. The
     * {@code games} field carries the run's expected total so the Games card can
     * show "played of total".
     *
     * @param wins candidate wins so far
     * @param draws draws so far
     * @param losses candidate losses so far
     * @param expectedGames total games the run will play
     * @param millis elapsed wall-clock time
     * @return live summary
     */
    static GauntletResultSummary live(int wins, int draws, int losses, int expectedGames, long millis) {
        int played = wins + draws + losses;
        Double scorePercent = played == 0 ? null : (wins + 0.5d * draws) / played * 100.0d;
        return new GauntletResultSummary(false, wins, draws, losses, expectedGames, scorePercent,
                liveElo(wins, draws, losses), "-", millis);
    }

    /**
     * Parses final gauntlet result text.
     *
     * @param output command output
     * @param exitCode process exit code
     * @param millis elapsed time
     * @return parsed summary
     */
    static GauntletResultSummary parse(String output, int exitCode, long millis) {
        String text = output == null ? "" : output;
        Pattern resultPattern = Pattern.compile("\\+(\\d+)\\s+=(\\d+)\\s+-(\\d+)\\s+of\\s+(\\d+)\\s+games");
        Matcher resultMatcher = resultPattern.matcher(text);
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int games = 0;
        if (resultMatcher.find()) {
            wins = Integer.parseInt(resultMatcher.group(1));
            draws = Integer.parseInt(resultMatcher.group(2));
            losses = Integer.parseInt(resultMatcher.group(3));
            games = Integer.parseInt(resultMatcher.group(4));
        }
        Double score = matchDouble(text, "Score:\\s+([0-9.]+)%");
        String elo = matchText(text, "Elo estimate:\\s+([+-]?\\d+)");
        return new GauntletResultSummary(
                true,
                wins,
                draws,
                losses,
                games,
                score,
                elo == null ? "-" : elo,
                exitCode == 0 ? "0" : "exit " + exitCode,
                millis);
    }

    /**
     * Returns the live point Elo string for a partial tally, or {@code "-"}
     * when it is not yet defined (no wins or no losses).
     *
     * @param wins candidate wins
     * @param draws draws
     * @param losses candidate losses
     * @return point Elo string, or {@code "-"}
     */
    private static String liveElo(int wins, int draws, int losses) {
        int played = wins + draws + losses;
        if (played == 0 || wins == 0 || losses == 0) {
            return "-";
        }
        double p = (wins + 0.5d * draws) / played;
        return String.format(Locale.ROOT, "%+.0f", eloOf(p) + 0.0d);
    }

    /**
     * Returns copy with error text.
     *
     * @param value error value
     * @return summary
     */
    GauntletResultSummary withError(String value) {
        return new GauntletResultSummary(complete, wins, draws, losses, games, scorePercent, elo, value, millis);
    }

    /**
     * Score card value.
     *
     * @return value
     */
    String scoreText() {
        return scorePercent == null ? "-" : String.format(Locale.ROOT, "%.1f%%", scorePercent.doubleValue());
    }

    /**
     * Score detail.
     *
     * @return detail
     */
    String scoreDetail() {
        if (complete) {
            return "candidate perspective";
        }
        return played() > 0 ? "candidate view · running" : "run a gauntlet";
    }

    /**
     * Returns the number of games played so far.
     *
     * @return played game count
     */
    private int played() {
        return wins + draws + losses;
    }

    /**
     * W-D-L value.
     *
     * @return value
     */
    String wdlText() {
        return wins + " / " + draws + " / " + losses;
    }

    /**
     * W-D-L detail.
     *
     * @return detail
     */
    String wdlDetail() {
        if (complete) {
            return "wins / draws / losses";
        }
        return played() > 0 ? "so far · wins / draws / losses" : "not complete";
    }

    /**
     * Elo value, with a 95% error margin when one is defined.
     *
     * @return value
     */
    String eloText() {
        if (elo == null || elo.isBlank()) {
            return "-";
        }
        Double margin = eloMargin();
        return margin == null ? elo : elo + " ± " + Math.round(margin);
    }

    /**
     * Elo detail. Reports the confidence basis so it is clear that more games
     * tighten the interval.
     *
     * @return detail
     */
    String eloDetail() {
        Double margin = eloMargin();
        return margin == null ? "point estimate" : "95% interval · " + played() + " games";
    }

    /**
     * Returns the symmetric 95% Elo error margin for the match result, or
     * {@code null} when it is undefined (no result, or an all-win/all-loss score
     * whose Elo estimate is already infinite).
     *
     * <p>
     * The margin shrinks as the game count grows, so a longer gauntlet yields a
     * more precise strength estimate.
     * </p>
     *
     * @return Elo error margin, or {@code null}
     */
    Double eloMargin() {
        int n = played();
        if (n <= 0 || wins <= 0 || losses <= 0) {
            return null;
        }
        double p = (wins + 0.5d * draws) / n;
        if (p <= 0.0d || p >= 1.0d) {
            return null;
        }
        // Per-game score variance over the {1, 0.5, 0} outcomes, then the
        // standard error of the mean score across the n games.
        double variance = (wins * square(1.0d - p) + draws * square(0.5d - p) + losses * square(p)) / n;
        double standardError = Math.sqrt(variance / n);
        double z = 1.959964d; // two-sided 95%
        double low = clampProbability(p - z * standardError);
        double high = clampProbability(p + z * standardError);
        return (eloOf(high) - eloOf(low)) / 2.0d;
    }

    /**
     * Games value.
     *
     * @return value
     */
    String gamesText() {
        if (!complete && played() > 0) {
            return Integer.toString(played());
        }
        return games <= 0 ? "-" : Integer.toString(games);
    }

    /**
     * Games detail.
     *
     * @return detail
     */
    String gamesDetail() {
        if (complete) {
            return "completed games";
        }
        return played() > 0 ? "of " + games + " · running" : "configured games";
    }

    /**
     * Errors value.
     *
     * @return value
     */
    String errorsText() {
        return errors == null || errors.isBlank() ? "-" : errors;
    }

    /**
     * Errors detail.
     *
     * @return detail
     */
    String errorsDetail() {
        if (complete) {
            return "process status";
        }
        return played() > 0 ? "running" : "unavailable until run";
    }

    /**
     * Duration value.
     *
     * @return value
     */
    String durationText() {
        return duration(millis);
    }

    /**
     * Duration detail.
     *
     * @return detail
     */
    String durationDetail() {
        return "wall-clock runtime";
    }

    /**
     * Returns first matched double.
     *
     * @param text source text
     * @param regex regex with one numeric capture
     * @return parsed value or null
     */
    private static Double matchDouble(String text, String regex) {
        String value = matchText(text, regex);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Returns first matched text.
     *
     * @param text source text
     * @param regex regex with one capture
     * @return capture or null
     */
    private static String matchText(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Formats milliseconds.
     *
     * @param millis milliseconds
     * @return duration
     */
    private static String duration(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        if (millis < 1000L) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0d);
    }

    /**
     * Returns the logistic Elo difference for a score fraction.
     *
     * @param fraction score fraction in {@code (0, 1)}
     * @return Elo difference
     */
    private static double eloOf(double fraction) {
        return -400.0d * Math.log10(1.0d / fraction - 1.0d);
    }

    /**
     * Clamps a probability away from the open-interval endpoints so the Elo
     * conversion stays finite.
     *
     * @param value raw probability
     * @return clamped probability
     */
    private static double clampProbability(double value) {
        return Math.min(0.999999d, Math.max(0.000001d, value));
    }

    /**
     * Returns the square of a value.
     *
     * @param value input
     * @return value squared
     */
    private static double square(double value) {
        return value * value;
    }
}
