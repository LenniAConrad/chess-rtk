package application.gui.workbench.play;

/**
 * Immutable description of how strong the engine opponent should play.
 *
 * <p>
 * The primary knob is {@link #targetElo()}, which {@link StrengthModel} maps to
 * both a search budget (Layer A) and the move-sampling parameters used to weaken
 * play believably (Layer B: temperature, top-k, blunder rate). The optional
 * {@code *Override} fields let an expert pin any derived value directly; a
 * {@code null} override means "derive from Elo". When {@link #deterministic()}
 * is set the engine always plays the arg-max move, which is also what the Elo
 * curve produces at maximum strength.
 * </p>
 *
 * @param targetElo requested playing strength on the slider scale
 * @param nodesOverride explicit playout budget, or {@code null} to derive from Elo
 * @param movetimeOverride explicit per-move time budget in ms, or {@code null}
 * @param temperatureOverride explicit sampling temperature, or {@code null}
 * @param blunderOverride explicit per-move blunder probability, or {@code null}
 * @param topKOverride explicit candidate count, or {@code null}
 * @param rngSeed seed for reproducible sampling and side resolution; 0 means random
 * @param deterministic when true, always play the arg-max move (no sampling)
 */
public record StrengthProfile(
        int targetElo,
        Integer nodesOverride,
        Integer movetimeOverride,
        Double temperatureOverride,
        Double blunderOverride,
        Integer topKOverride,
        long rngSeed,
        boolean deterministic) {

    /**
     * Creates an Elo-only profile with sampling enabled (the normal game mode).
     *
     * @param targetElo requested playing strength
     * @return Elo-only profile
     */
    public static StrengthProfile ofElo(int targetElo) {
        return ofElo(targetElo, false);
    }

    /**
     * Creates an Elo-only profile, optionally forcing deterministic arg-max play.
     *
     * @param targetElo requested playing strength
     * @param deterministic true to always play the arg-max move
     * @return Elo-only profile
     */
    public static StrengthProfile ofElo(int targetElo, boolean deterministic) {
        return new StrengthProfile(targetElo, null, null, null, null, null, 0L, deterministic);
    }
}
