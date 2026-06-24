package application.gui.workbench.play;

import application.gui.workbench.Defaults;
import java.util.prefs.Preferences;

/**
 * Thin typed wrapper around {@link Preferences} for Play-tab settings, mirroring
 * {@link application.gui.workbench.network.Prefs}. Persists the human's side,
 * target strength, and the deterministic toggle so a Play setup survives across
 * sessions.
 */
public final class PlayPrefs {

    /**
     * Preferences node used for Play-tab settings.
     */
    private static final Preferences NODE = Preferences.userRoot().node("crtk/workbench/play");

    /**
     * Side preference key.
     */
    private static final String KEY_SIDE = "side";

    /**
     * Strength (Elo) preference key.
     */
    private static final String KEY_ELO = "elo";

    /**
     * Deterministic-play preference key.
     */
    private static final String KEY_DETERMINISTIC = "deterministic";

    /**
     * Search-algorithm preference key.
     */
    private static final String KEY_SEARCH = "search";

    /**
     * Evaluation-network preference key.
     */
    private static final String KEY_NETWORK = "network";

    /**
     * Opening-book preference key.
     */
    private static final String KEY_OPENING_BOOK = "openingBook";

    /**
     * Utility class; prevents instantiation.
     */
    private PlayPrefs() {
        // utility
    }

    /**
     * Returns the persisted side index (0 White, 1 Black, 2 Random).
     *
     * @return side index, defaulting to White
     */
    public static int sideIndex() {
        return clamp(NODE.getInt(KEY_SIDE, 0), 0, 2);
    }

    /**
     * Persists the side index.
     *
     * @param index side index
     */
    public static void setSideIndex(int index) {
        NODE.putInt(KEY_SIDE, clamp(index, 0, 2));
    }

    /**
     * Returns the persisted target Elo.
     *
     * @return target Elo, defaulting to {@link Defaults#PLAY_ELO}
     */
    public static int elo() {
        return clamp(NODE.getInt(KEY_ELO, Defaults.PLAY_ELO), StrengthModel.MIN_ELO, StrengthModel.MAX_ELO);
    }

    /**
     * Persists the target Elo.
     *
     * @param elo target Elo
     */
    public static void setElo(int elo) {
        NODE.putInt(KEY_ELO, clamp(elo, StrengthModel.MIN_ELO, StrengthModel.MAX_ELO));
    }

    /**
     * Returns whether deterministic (arg-max) play is enabled.
     *
     * @return true when deterministic
     */
    public static boolean deterministic() {
        return NODE.getBoolean(KEY_DETERMINISTIC, false);
    }

    /**
     * Persists the deterministic-play toggle.
     *
     * @param value true for deterministic play
     */
    public static void setDeterministic(boolean value) {
        NODE.putBoolean(KEY_DETERMINISTIC, value);
    }

    /**
     * Returns whether the opening book is enabled (default true for real play).
     *
     * @return true when the opening book should answer engine moves
     */
    public static boolean openingBook() {
        return NODE.getBoolean(KEY_OPENING_BOOK, true);
    }

    /**
     * Persists the opening-book toggle.
     *
     * @param value true to enable the opening book
     */
    public static void setOpeningBook(boolean value) {
        NODE.putBoolean(KEY_OPENING_BOOK, value);
    }

    /**
     * Returns the persisted search algorithm, defaulting to alpha-beta.
     *
     * @return search algorithm
     */
    public static Opponent.Search search() {
        try {
            return Opponent.Search.valueOf(NODE.get(KEY_SEARCH, Opponent.Search.ALPHA_BETA.name()));
        } catch (IllegalArgumentException ex) {
            return Opponent.Search.ALPHA_BETA;
        }
    }

    /**
     * Persists the search algorithm.
     *
     * @param search search algorithm
     */
    public static void setSearch(Opponent.Search search) {
        NODE.put(KEY_SEARCH, search.name());
    }

    /**
     * Returns the persisted evaluation network, defaulting to classical.
     *
     * @return evaluation network
     */
    public static Opponent.Network network() {
        try {
            return Opponent.Network.valueOf(NODE.get(KEY_NETWORK, Opponent.Network.CLASSICAL.name()));
        } catch (IllegalArgumentException ex) {
            return Opponent.Network.CLASSICAL;
        }
    }

    /**
     * Persists the evaluation network.
     *
     * @param network evaluation network
     */
    public static void setNetwork(Opponent.Network network) {
        NODE.put(KEY_NETWORK, network.name());
    }

    /**
     * Clamps an integer to a range.
     *
     * @param value candidate value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
