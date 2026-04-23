package application.gui.studio;

import application.cli.Format;
import chess.core.Move;
import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Output;

/**
 * Immutable engine analysis update.
 *
 * @param version position version
 * @param configKey engine config key
 * @param status status label
 * @param eval eval label
 * @param depth depth label
 * @param nodes nodes label
 * @param time time label
 * @param pv principal variation text
 * @param bestMove first move of PV1
 * @param error true when update is an error
 */
public record StudioEngineSnapshot(
		long version,
		String configKey,
		String status,
		String eval,
		String depth,
		String nodes,
		String time,
		String pv,
		short bestMove,
		boolean error) {

	/**
	 * Builds a snapshot from analysis.
	 *
	 * @param version position version
	 * @param configKey config key
	 * @param position analyzed position
	 * @param analysis analysis buffer
	 * @param status status label
	 * @return snapshot
	 */
	public static StudioEngineSnapshot fromAnalysis(long version, String configKey, Position position,
			Analysis analysis, String status) {
		Output output = analysis == null ? null : analysis.getBestOutput();
		if (output == null) {
			return new StudioEngineSnapshot(version, configKey, status, "-", "-", "-", "-", "", Move.NO_MOVE, false);
		}
		short[] moves = output.getMoves();
		short best = moves == null || moves.length == 0 ? Move.NO_MOVE : moves[0];
		return new StudioEngineSnapshot(
				version,
				configKey,
				status,
				Format.formatEvaluation(output.getEvaluation()),
				output.getDepth() + "/" + output.getSelectiveDepth(),
				application.cli.command.CommandSupport.formatCount(output.getNodes()),
				output.getTime() + " ms",
				Format.formatPvMovesSan(position, moves),
				best,
				false);
	}

	/**
	 * Builds an error snapshot.
	 *
	 * @param version position version
	 * @param configKey config key
	 * @param message error message
	 * @return error snapshot
	 */
	public static StudioEngineSnapshot error(long version, String configKey, String message) {
		return new StudioEngineSnapshot(version, configKey, "Error", "-", "-", "-", "-",
				message == null ? "" : message, Move.NO_MOVE, true);
	}

	/**
	 * Tests whether this update matches the current state.
	 *
	 * @param currentVersion current position version
	 * @param currentConfigKey current config key
	 * @return true when matching
	 */
	public boolean isCurrent(long currentVersion, String currentConfigKey) {
		return version == currentVersion && configKey.equals(currentConfigKey);
	}
}
