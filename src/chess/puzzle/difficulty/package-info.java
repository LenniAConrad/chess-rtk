/**
 * Puzzle difficulty scoring and feature extraction.
 *
 * <p>
 * The package produces deterministic, Elo-like puzzle ratings from engine
 * analysis, inexpensive visibility checks, and explicit solution-tree evidence.
 * Ratings are direct puzzle estimates and are not remapped by the exported
 * subset.
 * </p>
 *
 * <p>
 * <strong>Warning:</strong> these ratings are heuristic puzzle labels. They are
 * useful for ordering and bucketing generated puzzles, but they are not
 * interchangeable with human federation ratings or online chess ratings.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
package chess.puzzle.difficulty;
