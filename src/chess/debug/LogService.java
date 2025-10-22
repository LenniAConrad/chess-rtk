package chess.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Used for centralised application logging that writes to a file using a
 * custom&nbsp;format and
 * flushes after each log entry.
 * 
 * @author Lennart A. Conrad
 */
public final class LogService {

	/**
	 * Used for formatting timestamps in log entries.
	 */
	private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

	/**
	 * Used for padding multi-line log entries so that lines after the first
	 */
	private static final String TIME_PATTERN_SPACE = " ".repeat(TIME_PATTERN.length() + 1);

	/**
	 * Used for recording log events.
	 */
	private static final Logger LOGGER = Logger.getLogger(LogService.class.getName());

	/**
	 * Used for writing log events to {@code application.log}.
	 */
	private static final FileHandler FILE_HANDLER;

	static {
		FileHandler handler = null;
		try {
			Path logDir = Paths.get("session");
			Files.createDirectories(logDir);

			Path logFile = logDir.resolve(System.currentTimeMillis() + ".log");
			handler = new FileHandler(logFile.toString(), true);
			handler.setFormatter(new LogFormatter());
			handler.setLevel(Level.ALL);
			handler.setEncoding("UTF-8");

			LOGGER.addHandler(handler);
			LOGGER.setUseParentHandlers(false);
			LOGGER.setLevel(Level.ALL);
		} catch (IOException ex) {
			Logger.getAnonymousLogger()
				.log(Level.SEVERE, "Used for reporting file-handler initialisation failure.", ex);
		}
		FILE_HANDLER = handler;
		}

	/**
	 * Used for preventing instantiation of this utility class.
	 */
	private LogService() {

	}

	/**
	 * Used for logging an array of informational messages where the first line is
	 * timestamped and each
	 * subsequent line is padded so that the timestamp column aligns with the space
	 * preceding the log
	 * level.
	 *
	 * @param messages the messages to log
	 */
	public static void info(String... messages) {
		if (messages == null || messages.length == 0) {
			return;
		}

		StringBuilder builder = new StringBuilder(messages[0]);
		String levelPrefix = Level.INFO + " ";

		for (int i = 1; i < messages.length; i++) {
			builder.append(System.lineSeparator())
					.append(TIME_PATTERN_SPACE)
					.append(levelPrefix)
					.append(messages[i]);
		}

		LOGGER.log(Level.INFO, "{0}", builder);
		flush();
	}

	/**
	 * Used for logging a warning message.
	 *
	 * @param message the message to log
	 */
	public static void warn(String... messages) {
		if (messages == null || messages.length == 0) {
			return;
		}

		StringBuilder builder = new StringBuilder(messages[0]);
		String levelPrefix = Level.WARNING + " ";

		for (int i = 1; i < messages.length; i++) {
			builder.append(System.lineSeparator())
					.append(TIME_PATTERN_SPACE)
					.append(levelPrefix)
					.append(messages[i]);
		}

		LOGGER.log(Level.WARNING, "{0}", builder);
		flush();
	}

	/**
	 * Used for logging an error message including an optional {@link Throwable}
	 * cause.
	 * <p>
	 * The first element is logged as-is; subsequent elements are placed on new
	 * lines
	 * with timestamp/level-aligned padding for readability.
	 *
	 * @param throwable an optional exception to log (may be {@code null})
	 * @param messages  one or more message lines to log
	 */
	public static void error(Throwable throwable, String... messages) {
		if ((messages == null || messages.length == 0) && throwable == null) {
			return;
		}

		String msg;
		if (messages != null && messages.length > 0) {
			StringBuilder builder = new StringBuilder(messages[0]);
			String levelPrefix = Level.SEVERE + " ";
			for (int i = 1; i < messages.length; i++) {
				builder.append(System.lineSeparator())
						.append(TIME_PATTERN_SPACE)
						.append(levelPrefix)
						.append(messages[i]);
			}
			msg = builder.toString();
		} else {
			// No message lines: fall back to throwable message (if any)
			msg = (throwable != null && throwable.getMessage() != null)
					? throwable.getMessage()
					: "";
		}

		if (throwable != null) {
			LOGGER.log(Level.SEVERE, msg, throwable);
		} else {
			LOGGER.log(Level.SEVERE, "{0}", msg);
		}
		flush();
	}

	/**
	 * Used for flushing the {@link FileHandler} buffer to disk.
	 */
	private static void flush() {
		if (FILE_HANDLER != null) {
			FILE_HANDLER.flush();
		}
	}

	/**
	 * Used for formatting log records as
	 * {@code "HH:mm:ss (yyyy-MM-dd) LEVEL MESSAGE"}.
	 */
	private static final class LogFormatter extends Formatter {

		/**
		 * Used for formatting {@link LogRecord}s into a single-line string.
		 *
		 * @param record the log record
		 * @return the formatted string
		 */
		@Override
		public String format(LogRecord logRecord) {
			LocalDateTime dateTime = LocalDateTime.now();
			String timestamp = dateTime.format(DateTimeFormatter.ofPattern(TIME_PATTERN));
			return String.format("%s %s %s%n", timestamp, logRecord.getLevel(), formatMessage(logRecord));
		}
	}

	/**
	 * Returns a canonical absolute path string for logging.
	 * <p>
	 * Tries {@link Path#toRealPath(LinkOption...)} (without following symlinks).
	 * If that fails, falls back to {@link Path#toAbsolutePath()} and
	 * {@link Path#normalize()}.
	 * <p>
	 * Returns the literal string {@code "null"} if input is {@code null}.
	 *
	 * @param p path to render, may be {@code null}
	 * @return canonical/absolute path string, or {@code "null"}
	 */
	public static String pathAbs(Path p) {
		if (p == null)
			return "null";
		try {
			return p.toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
		} catch (Exception e) {
			return p.toAbsolutePath().normalize().toString();
		}
	}

	/**
	 * Overload for string paths, delegating to {@link #pathAbs(Path)}.
	 *
	 * @param path path string, may be {@code null}
	 * @return canonical/absolute path string, or {@code "null"}
	 */
	public static String pathAbs(String path) {
		return pathAbs(path == null ? null : Paths.get(path));
	}
}
