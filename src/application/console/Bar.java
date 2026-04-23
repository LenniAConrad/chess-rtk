package application.console;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used for drawing a textual progress bar with ETA information.
 *
 * <p>
 * Designed for multi-threaded producers that call {@link #step()} whenever a
 * unit of work completes. All rendering is serialized so concurrent bars do not
 * interleave their output.
 * </p>
 * <p>
 * By default the bar uses Unicode block glyphs for a dense visual; pass
 * {@code ascii=true} to {@link #Bar(int, String, boolean)} for environments
 * without UTF-8 support.
 * </p>
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Bar {

	/**
	 * Used for serializing console writes so different bars do not interleave.
	 */
	private static final ReentrantLock RENDER_LOCK = new ReentrantLock();

	/**
	 * Used for controlling how many characters make up the visual bar.
	 */
	private static final int BAR_WIDTH = 64;

	/**
	 * Glyphs for partial block rendering (index is eighths filled).
	 */
	private static final char[] BLOCKS = {
			'\u0020', // space / empty
			'\u258F', // left one eighth block
			'\u258E', // left one quarter block
			'\u258D', // left three eighths block
			'\u258C', // left half block
			'\u258B', // left five eighths block
			'\u258A', // left three quarters block
			'\u2589', // left seven eighths block
			'\u2588'  // full block
	};

	/**
	 * Used for capping redraw frequency (max ~120 updates per second).
	 */
	private static final long MAX_UPDATES_PER_SECOND = 120;

	/**
	 * Minimum nanoseconds between renders (8_333_333 ns @ 120 Hz).
	 */
	private static final long MIN_RENDER_INTERVAL_NS = Math.max(1L, 1_000_000_000L / MAX_UPDATES_PER_SECOND);

	/**
	 * Used for tracking the total units of work being monitored.
	 */
	private final long total;

	/**
	 * How often to render progress (throttled for large totals).
	 */
	private final long renderEvery;

	/**
	 * Optional label prefix (e.g., "Perturb: ").
	 */
	private final String labelPrefix;

	/**
	 * Optional trailing status text (e.g., "samples=536").
	 */
	private volatile String postfix = "";

	/**
	 * Whether to render with plain ASCII (fallback) instead of block glyphs.
	 */
	private final boolean useAscii;

	/**
	 * Stream used for progress rendering.
	 */
	private final PrintStream stream;

	/**
	 * Used for counting the number of completed units.
	 */
	private final AtomicLong completed = new AtomicLong();

	/**
	 * Used for throttling render frequency.
	 */
	private volatile long lastRenderNs = 0L;

	/**
	 * Used for tracking the length of the last rendered line for cleanup.
	 */
	private int lastRenderLength = 0;

	/**
	 * Used for capturing the start timestamp for elapsed/ETA calculations.
	 */
	private final long startNs;

	/**
	 * @param total total amount of work to track; when zero or negative, rendering
	 *              stays disabled
	 */
	public Bar(int total) {
		this((long) total, null, false);
	}

	/**
	 * @param total total amount of work to track; when zero or negative, rendering
	 *              stays disabled
	 * @param label optional label prefix displayed before the bar
	 */
	public Bar(int total, String label) {
		this((long) total, label, false);
	}

	/**
	 * @param total   total amount of work to track; when zero or negative, rendering
	 *                stays disabled
	 * @param label   optional label prefix displayed before the bar
	 * @param ascii   when true, render the bar with ASCII characters instead of
	 *                Unicode blocks (useful for terminals that do not support UTF-8)
	 */
	public Bar(int total, String label, boolean ascii) {
		this((long) total, label, ascii);
	}

	/**
	 * @param total total amount of work to track; when zero or negative, rendering
	 *              stays disabled
	 * @param label optional label prefix displayed before the bar
	 */
	public Bar(long total, String label) {
		this(total, label, false);
	}

	/**
	 * @param total total amount of work to track; when zero or negative, rendering
	 *              stays disabled
	 * @param label optional label prefix displayed before the bar
	 * @param ascii when true, render the bar with ASCII characters instead of
	 *              Unicode blocks
	 */
	public Bar(long total, String label, boolean ascii) {
		this(total, label, ascii, System.out);
	}

	/**
	 * @param total total amount of work to track; when zero or negative, rendering
	 *              stays disabled
	 * @param label optional label prefix displayed before the bar
	 * @param ascii when true, render the bar with ASCII characters instead of
	 *              Unicode blocks
	 * @param stream stream used for progress rendering
	 */
	public Bar(long total, String label, boolean ascii, PrintStream stream) {
		this.total = Math.max(0, total);
		this.labelPrefix = (label == null || label.isBlank()) ? "" : label.trim() + ": ";
		this.useAscii = ascii;
		this.stream = stream == null ? System.out : stream;
		this.renderEvery = chooseRenderEvery(this.total);
		this.startNs = System.nanoTime();
		if (this.total > 0) {
			renderMaybe(0, System.nanoTime(), true, true, true);
		}
	}

	/**
	 * Used for advancing the bar by one unit and re-rendering the output.
	 */
	public void step() {
		if (total <= 0) {
			return;
		}
		long done = completed.incrementAndGet();
		long now = System.nanoTime();
		renderMaybe(done, now, false, false, false);
	}

	/**
	 * Used for advancing the bar by one unit while updating the trailing status text
	 * before the render.
	 *
	 * @param postfix text to append inside the brackets; blank/empty clears it
	 */
	public void step(String postfix) {
		if (total <= 0) {
			return;
		}
		this.postfix = (postfix == null) ? "" : postfix.trim();
		long done = completed.incrementAndGet();
		long now = System.nanoTime();
		renderMaybe(done, now, false, false, false);
	}

	/**
	 * Used for advancing the bar by a variable number of units.
	 *
	 * @param amount completed units to add
	 */
	public void step(long amount) {
		if (total <= 0 || amount <= 0) {
			return;
		}
		long done = completed.addAndGet(amount);
		long now = System.nanoTime();
		renderMaybe(done, now, false, false, false);
	}

	/**
	 * Used for setting absolute completion, useful for file byte counters.
	 *
	 * @param done absolute completed units
	 */
	public void set(long done) {
		if (total <= 0) {
			return;
		}
		long clamped = Math.max(0L, Math.min(done, total));
		completed.set(clamped);
		renderMaybe(clamped, System.nanoTime(), false, false, false);
	}

	/**
	 * Used for updating the trailing status text (e.g., extra counters).
	 *
	 * @param postfix text to append inside the brackets; blank/empty clears it
	 */
	public void setPostfix(String postfix) {
		if (total <= 0) {
			return;
		}
		this.postfix = (postfix == null) ? "" : postfix.trim();
		renderMaybe(completed.get(), System.nanoTime(), true, false, false);
	}

	/**
	 * Used for printing a trailing newline once the bar is complete.
	 */
	public void finish() {
		if (total <= 0) {
			return;
		}
		long done = completed.get();
		if (done < total || lastRenderLength == 0) {
			renderMaybe(done, System.nanoTime(), true, true, true);
		}
		RENDER_LOCK.lock();
		try {
			stream.println();
			lastRenderLength = 0;
		} finally {
			RENDER_LOCK.unlock();
		}
	}

	/**
	 * Used for handling throttled render decisions and invoking the renderer.
	 *
	 * @param done              completed units of work
	 * @param now               caller nanotime (avoids double nanoTime() calls)
	 * @param ignoreRenderEvery ignore the renderEvery modulus gate
	 * @param ignoreThrottle    ignore the time-based throttle (e.g., final render)
	 * @param forceWriteLock    when true, block to acquire the render lock
	 */
	private void renderMaybe(long done, long now, boolean ignoreRenderEvery, boolean ignoreThrottle, boolean forceWriteLock) {
		if (total <= 0) {
			return;
		}
		if (!shouldRender(done, now, ignoreRenderEvery, ignoreThrottle)) {
			return;
		}
		final long clamped = Math.max(0L, Math.min(done, total));
		renderProgress(clamped, now, forceWriteLock);
	}

	/**
	 * Used for computing the estimated time remaining for completion.
	 *
	 * @param done completed units
	 * @return duration until completion or {@code null} when not enough data
	 */
	private Duration computeEta(long done) {
		if (done <= 0 || done > total) {
			return null;
		}
		final long elapsedNs = Math.max(0L, System.nanoTime() - startNs);
		if (elapsedNs == 0) {
			return null;
		}
		final long remaining = total - done;
		if (remaining <= 0) {
			return Duration.ZERO;
		}
		final double meanPerTask = elapsedNs / (double) done;
		final long etaNs = (long) (meanPerTask * remaining);
		return etaNs <= 0 ? Duration.ZERO : Duration.ofNanos(etaNs);
	}

	/**
	 * Used for computing how long the bar has been running.
	 *
	 * @return elapsed duration since construction
	 */
	private Duration computeElapsed() {
		long elapsedNs = Math.max(0L, System.nanoTime() - startNs);
		return Duration.ofNanos(elapsedNs);
	}

	/**
	 * Used for deciding whether to render based on throttling.
	 */
	private boolean shouldRender(long done, long now, boolean ignoreRenderEvery, boolean ignoreThrottle) {
		if (done >= total) {
			return true;
		}
		if (!ignoreThrottle && lastRenderNs > 0 && (now - lastRenderNs) < MIN_RENDER_INTERVAL_NS) {
			return false;
		}
		if (ignoreRenderEvery || renderEvery <= 1) {
			return true;
		}
		return done % renderEvery == 0;
	}

	/**
	 * Used for choosing a sensible render frequency for large totals.
	 */
	private static long chooseRenderEvery(long total) {
		if (total <= 0) {
			return 1;
		}
		if (total >= 5_000_000) {
			return 10_000;
		}
		if (total >= 50_000) {
			return 503;
		}
		return 1;
	}

	/**
	 * Used for formatting elapsed/ETA durations into HH:MM:SS (or MM:SS) strings.
	 *
	 * @param duration duration to format; {@code null} returns {@code "--:--"}
	 * @return formatted duration string
	 */
	private static String formatDuration(Duration duration) {
		return formatDuration(duration, "--:--");
	}

	/**
	 * Used for formatting elapsed/ETA durations into HH:MM:SS (or MM:SS) strings.
	 *
	 * @param duration duration to format
	 * @param unknown  fallback string when duration is {@code null}
	 * @return formatted duration string
	 */
	private static String formatDuration(Duration duration, String unknown) {
		if (duration == null) {
			return unknown;
		}
		long seconds = Math.max(0, duration.getSeconds());
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs = seconds % 60;
		if (hours > 0) {
			return String.format("%d:%02d:%02d", hours, minutes, secs);
		}
		return String.format("%02d:%02d", minutes, secs);
	}

	/**
	 * Used for rendering the tqdm-style line with percentage, counts, elapsed/ETA,
	 * throughput, and optional postfix.
	 *
	 * @param clamped completed units, clamped to {@code [0, total]}
	 * @param now     current nanotime (for throttle bookkeeping)
	 * @param forceWriteLock block and render even if another bar is writing
	 */
	private void renderProgress(long clamped, long now, boolean forceWriteLock) {
		final double fraction = (total == 0) ? 0.0 : (double) clamped / total;
		final int percent = (int) Math.floor(fraction * 100.0);
		final String bar = useAscii ? buildAsciiBar(fraction) : buildBlockBar(fraction);

		final Duration elapsed = computeElapsed();
		final Duration eta = computeEta(clamped);
		final String rate = formatRate(clamped, elapsed);
		final StringBuilder line = new StringBuilder(labelPrefix.length() + 64);

		line.append(labelPrefix);
		line.append(String.format("%3d%%|%s| %d/%d [", percent, bar, clamped, total));
		line.append(formatDuration(elapsed));
		line.append("<");
		line.append(formatDuration(eta, "?"));
		line.append(", ");
		line.append(rate);
		if (postfix != null && !postfix.isBlank()) {
			line.append(", ").append(postfix);
		}
		line.append("]");

		writeLine(line.toString(), forceWriteLock);
		lastRenderNs = now;
	}

	/**
	 * Used for constructing an ASCII bar with '=' fill and '>' pointer.
	 *
	 * @param fraction completion fraction [0,1]
	 * @return bar string of width {@link #BAR_WIDTH}
	 */
	private String buildAsciiBar(double fraction) {
		final int filled = (int) Math.round(fraction * BAR_WIDTH);
		final StringBuilder bar = new StringBuilder(BAR_WIDTH);
		for (int i = 0; i < BAR_WIDTH; i++) {
			if (i < filled) {
				bar.append('=');
			} else if (fraction < 1.0 && i == filled) {
				bar.append('>');
			} else {
				bar.append(' ');
			}
		}
		return bar.toString();
	}

	/**
	 * Used for constructing a Unicode bar with partial block glyphs (1/8th steps).
	 *
	 * @param fraction completion fraction [0,1]
	 * @return bar string of width {@link #BAR_WIDTH}
	 */
	private String buildBlockBar(double fraction) {
		final double clamped = Math.max(0.0, Math.min(1.0, fraction));
		double totalBlocks = clamped * BAR_WIDTH;
		int whole = (int) Math.floor(totalBlocks);
		int partial = (int) Math.round((totalBlocks - whole) * 8);
		if (partial == 8) {
			whole = Math.min(BAR_WIDTH, whole + 1);
			partial = 0;
		}
		if (whole >= BAR_WIDTH) {
			whole = BAR_WIDTH;
			partial = 0;
		}

		final StringBuilder bar = new StringBuilder(BAR_WIDTH);
		for (int i = 0; i < BAR_WIDTH; i++) {
			if (i < whole) {
				bar.append(BLOCKS[8]);
			} else if (i == whole && partial > 0) {
				bar.append(BLOCKS[partial]);
			} else {
				bar.append(BLOCKS[0]);
			}
		}
		return bar.toString();
	}

	/**
	 * Used for computing the human-readable throughput string (iterations per
	 * second).
	 *
	 * @param done    completed units
	 * @param elapsed elapsed duration
	 * @return formatted throughput, e.g., {@code "15.26it/s"}
	 */
	private static String formatRate(long done, Duration elapsed) {
		if (done <= 0) {
			return "?it/s";
		}
		double seconds = Math.max(0.001, elapsed.toMillis() / 1000.0);
		double perSecond = done / seconds;
		return String.format("%.2fit/s", perSecond);
	}

	/**
	 * Used for writing a single carriage-return-terminated line, clearing any
	 * longer previous line to prevent visual artifacts.
	 *
	 * @param line textual line to emit (without newline)
	 */
	private void writeLine(String line, boolean force) {
		boolean locked = force ? lockInterruptibly() : RENDER_LOCK.tryLock();
		if (!locked) {
			return; // Skip render if another writer is active and we won't block
		}
		try {
			stream.print("\r");
			if (lastRenderLength > 0 && line.length() < lastRenderLength) {
				stream.print(" ".repeat(lastRenderLength));
				stream.print("\r");
			}
			stream.print(line);
			lastRenderLength = line.length();
			stream.flush();
		} finally {
			RENDER_LOCK.unlock();
		}
	}

	/**
	 * Attempts to acquire the render lock while preserving interrupt status.
	 *
	 * @return {@code true} when the lock was acquired; {@code false} if interrupted
	 */
	private boolean lockInterruptibly() {
		try {
			RENDER_LOCK.lockInterruptibly();
			return true;
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
