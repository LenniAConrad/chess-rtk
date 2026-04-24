package application.gui.studio;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Protocol;

/**
 * Cancellable one-engine analysis service for GUI v3.
 */
public final class StudioEngineService {

	/**
	 * Current background analysis worker.
	 */
	private SwingWorker<Void, StudioEngineSnapshot> worker;

	/**
	 * Reused engine instance for the active profile path.
	 */
	private Engine engine;

	/**
	 * Normalized path key for the reused engine.
	 */
	private String enginePathKey = "";

	/**
	 * Starts live analysis.
	 *
	 * @param profile engine profile
	 * @param position position snapshot
	 * @param version position version
	 * @param nodes search nodes fallback for bounded engines
	 * @param multipv multipv
	 * @param wdl show WDL
	 * @param onUpdate update callback
	 */
	public void start(StudioEngineProfile profile, Position position, long version, long nodes, int multipv,
			boolean wdl, Consumer<StudioEngineSnapshot> onUpdate) {
		stop();
		String configKey = configKey(profile, nodes, multipv, wdl);
		worker = new SwingWorker<>() {
			/**
			 * Runs engine analysis away from the Swing event thread.
			 *
			 * @return always null
			 */
			@Override
			protected Void doInBackground() {
				try {
					Engine active = ensureEngine(profile);
					active.newGame();
					active.setMultiPivot(Math.max(1, multipv));
					active.showWinDrawLoss(wdl);
					Analysis analysis = new Analysis();
					active.analyseInfinite(position.copy(), analysis, null, 5_000L, this::isCancelled,
							next -> publish(StudioEngineSnapshot.fromAnalysis(version, configKey, position, next,
									"Running")));
					publish(StudioEngineSnapshot.fromAnalysis(version, configKey, position, analysis, "Stopped"));
				} catch (Exception ex) {
					publish(StudioEngineSnapshot.error(version, configKey, ex.getMessage()));
				}
				return null;
			}

			/**
			 * Delivers engine snapshots on the Swing event thread.
			 *
			 * @param chunks published snapshots
			 */
			@Override
			protected void process(List<StudioEngineSnapshot> chunks) {
				if (onUpdate != null) {
					for (StudioEngineSnapshot update : chunks) {
						onUpdate.accept(update);
					}
				}
			}
		};
		worker.execute();
	}

	/**
	 * Stops analysis.
	 */
	public void stop() {
		if (worker != null && !worker.isDone()) {
			worker.cancel(true);
		}
	}

	/**
	 * Closes engine resources.
	 */
	public void close() {
		stop();
		if (engine != null) {
			engine.close();
			engine = null;
			enginePathKey = "";
		}
	}

	/**
	 * Returns whether analysis is running.
	 *
	 * @return true when running
	 */
	public boolean isRunning() {
		return worker != null && !worker.isDone();
	}

	/**
	 * Builds a config key.
	 *
	 * @param profile engine profile
	 * @param nodes nodes
	 * @param multipv multipv
	 * @param wdl wdl flag
	 * @return key
	 */
	public static String configKey(StudioEngineProfile profile, long nodes, int multipv, boolean wdl) {
		return profile.path().toAbsolutePath().normalize() + "|nodes=" + nodes + "|multipv="
				+ multipv + "|wdl=" + wdl;
	}

	/**
	 * Returns a reusable engine for the requested profile.
	 *
	 * @param profile engine profile
	 * @return engine instance
	 * @throws IOException when the protocol file cannot be read
	 */
	private Engine ensureEngine(StudioEngineProfile profile) throws IOException {
		String key = profile.path().toAbsolutePath().normalize().toString();
		if (engine != null && key.equals(enginePathKey)) {
			return engine;
		}
		if (engine != null) {
			engine.close();
		}
		Protocol protocol = new Protocol().fromToml(Files.readString(profile.path()));
		engine = new Engine(protocol);
		enginePathKey = key;
		return engine;
	}
}
