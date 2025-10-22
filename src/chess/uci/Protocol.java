package chess.uci;

import java.util.Arrays;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import utility.Dates;
import utility.Toml;

/**
 * Encapsulates the full UCI command-and-response protocol for a specific chess
 * engine implementation.
 * <p>
 * Different chess {@code Engine}s may use varying UCI command strings and
 * outputs. This class
 * serves as a configurable container for those differences, holding all
 * protocol-specific
 * commands (e.g., "uci", "go depth %d", "position %s") and expected responses
 * (e.g., "uciok" and
 * "readyok".
 * </p>
 * <p>
 * Protocol instances are typically initialized from a TOML configuration and
 * allow
 * validation, duplication, and TOML generation. After construction, this class
 * is
 * thread-safe for read access but not for concurrent mutation.
 * </p>
 *
 * @since 2024
 * @author Lennart A. Conrad
 */
public class Protocol {

	/**
	 * Used for identifying the key for the engine executable path.
	 */
	private static final String KEY_PATH = "path";

	/**
	 * Used for identifying the key for the engine name.
	 */
	private static final String KEY_NAME = "name";

	/**
	 * Used for identifying the key for the engine's configuration overrides.
	 */
	private static final String KEY_SETTINGS = "settings";

	/**
	 * Used for identifying the key for the UCI isready command.
	 */
	private static final String KEY_ISREADY = "isready";

	/**
	 * Used for identifying the key for the UCI readyok response.
	 */
	private static final String KEY_READYOK = "readyok";

	/**
	 * Used for identifying the key for the UCI search depth command.
	 */
	private static final String KEY_SEARCH_DEPTH = "searchDepth";

	/**
	 * Used for identifying the key for the UCI search nodes command.
	 */
	private static final String KEY_SEARCH_NODES = "searchNodes";

	/**
	 * Used for identifying the key for the UCI search time command.
	 */
	private static final String KEY_SEARCH_TIME = "searchTime";

	/**
	 * Used for identifying the key for the UCI position command.
	 */
	private static final String KEY_POSITION = "setPosition";

	/**
	 * Used for identifying the key for the UCI stop command.
	 */
	private static final String KEY_STOP = "stop";

	/**
	 * Used for identifying the key for the UCI new game command.
	 */
	private static final String KEY_NEW_GAME = "newGame";

	/**
	 * Used for identifying the key for enabling or disabling Chess960.
	 */
	private static final String KEY_CHESS960 = "setChess960";

	/**
	 * Used for identifying the key for setting the transposition table size.
	 */
	private static final String KEY_HASH_SIZE = "setHashSize";

	/**
	 * Used for identifying the key for setting the number of multi-pivot
	 * variations.
	 */
	private static final String KEY_MULTI_PIVOT_AMOUNT = "setMultiPivotAmount";

	/**
	 * Used for identifying the key for setting the thread count.
	 */
	private static final String KEY_THREAD_AMOUNT = "setThreadAmount";

	/**
	 * Used for identifying the key for displaying UCI options.
	 */
	private static final String KEY_SHOW_UCI = "showUci";

	/**
	 * Used for identifying the key for enabling or disabling WDL stats.
	 */
	private static final String KEY_SHOW_WDL = "showWinDrawLoss";

	/**
	 * Used for identifying the key for the setup command block.
	 */
	private static final String KEY_SETUP = "setup";

	/**
	 * The path to the chess {@code Engine} executable.
	 */
	protected String path = "insert path to your executable";

	/**
	 * The UCI command to check if the chess engine is ready.
	 */
	protected String isready = KEY_ISREADY;

	/**
	 * Used for storing the name of the {@code Engine}. Names often contain versions
	 * and useful information about chess engines.
	 */
	protected String name = "insert name of your engine";

	/**
	 * The UCI command to start a new game.
	 */
	protected String newGame = "ucinewgame";

	/**
	 * The UCI response indicating that the chess engine is ready.
	 */
	protected String readyok = KEY_READYOK;

	/**
	 * The UCI command to search for the best move to a specified depth.
	 */
	protected String searchDepth = "go depth %d";

	/**
	 * The UCI command to search for the best move by examining a specified number
	 * of nodes.
	 */
	protected String searchNodes = "go nodes %d";

	/**
	 * The UCI command to search for the best move within a specified time limit.
	 */
	protected String searchTime = "go movetime %d";

	/**
	 * The UCI command to enable or disable Chess960 mode.
	 */
	protected String setChess960 = "setoption name UCI_Chess960 value %b";

	/**
	 * The UCI command to set the size of the transposition table.
	 */
	protected String setHashSize = "setoption name Hash value %d";

	/**
	 * The UCI command to set the number of variations that the chess engine should
	 * examine.
	 */
	protected String setMultiPivotAmount = "setoption name multipv value %d";

	/**
	 * The UCI command to set the position using a FEN string.
	 */
	protected String setPosition = "position fen %s";

	/**
	 * The UCI command to set the number of threads that the chess engine can use.
	 */
	protected String setThreadAmount = "setoption name threads value %d";

	/**
	 * Changes made from the default {@code Engine}.
	 */
	protected String settings = "describe settings of engine";

	/**
	 * The UCI command to show the engine's UCI options.
	 */
	protected String showUci = "uci";

	/**
	 * The UCI command to enable or disable showing win/draw/loss statistics.
	 */
	protected String showWinDrawLoss = "setoption name UCI_ShowWDL value %b";

	/**
	 * The UCI command to stop the current search.
	 */
	protected String stop = KEY_STOP;

	/**
	 * The inputs that will be fed into the {@code Engine} once it is ready.
	 */
	protected String[] setup = new String[] { "setoption name UCI_ShowWDL value true" };

	/**
	 * Used for creating a {@code Protocol} from a TOML {@code String}.
	 *
	 * @param tomlContent the TOML text representing a Protocol
	 * @return a {@code Protocol} instance parsed from TOML
	 * @throws IOException if TOML parsing fails
	 */
	public Protocol fromToml(String tomlContent) throws IOException {
		Toml parser = Toml.load(new StringReader(tomlContent));

		// always-assigned fields
		this.path = parser.getString(KEY_PATH);
		this.name = parser.getString(KEY_NAME);
		this.settings = parser.getString(KEY_SETTINGS);
		this.isready = parser.getString(KEY_ISREADY);
		this.readyok = parser.getString(KEY_READYOK);
		this.stop = parser.getString(KEY_STOP);
		this.newGame = parser.getString(KEY_NEW_GAME);
		this.showUci = parser.getString(KEY_SHOW_UCI);

		// only-if-valid placeholders
		String v;
		if ((v = validPlaceholder(parser, KEY_SEARCH_DEPTH, "%d")) != null) {
			this.searchDepth = v;
		}
		if ((v = validPlaceholder(parser, KEY_SEARCH_NODES, "%d")) != null) {
			this.searchNodes = v;
		}
		if ((v = validPlaceholder(parser, KEY_SEARCH_TIME, "%d")) != null) {
			this.searchTime = v;
		}
		if ((v = validPlaceholder(parser, KEY_POSITION, "%s")) != null) {
			this.setPosition = v;
		}
		if ((v = validPlaceholder(parser, KEY_CHESS960, "%b")) != null) {
			this.setChess960 = v;
		}
		if ((v = validPlaceholder(parser, KEY_HASH_SIZE, "%d")) != null) {
			this.setHashSize = v;
		}
		if ((v = validPlaceholder(parser, KEY_MULTI_PIVOT_AMOUNT, "%d")) != null) {
			this.setMultiPivotAmount = v;
		}
		if ((v = validPlaceholder(parser, KEY_THREAD_AMOUNT, "%d")) != null) {
			this.setThreadAmount = v;
		}
		if ((v = validPlaceholder(parser, KEY_SHOW_WDL, "%b")) != null) {
			this.showWinDrawLoss = v;
		}

		// optional setup array
		List<String> setupList = parser.getStringList(KEY_SETUP);
		if (setupList != null) {
			this.setup = setupList.toArray(new String[0]);
		}

		return this;
	}

	/**
	 * Returns the raw TOML value for `key` if—and only if—it contains exactly one
	 * occurrence of `placeholder` and no other ‘%’ characters; otherwise null.
	 */
	private String validPlaceholder(Toml parser, String key, String placeholder) {
		String raw = parser.getString(key);
		if (raw != null
				&& countMatches(raw, placeholder) == 1
				&& countMatches(raw, "%") == 1) {
			return raw;
		}
		return null;
	}

	/**
	 * Used for constructing a deep copy of another {@code Protocol} instance.
	 *
	 * @param other the Protocol instance to copy
	 */
	public Protocol(Protocol other) {
		this.path = other.path;
		this.isready = other.isready;
		this.name = other.name;
		this.newGame = other.newGame;
		this.readyok = other.readyok;
		this.searchDepth = other.searchDepth;
		this.searchNodes = other.searchNodes;
		this.searchTime = other.searchTime;
		this.setChess960 = other.setChess960;
		this.setHashSize = other.setHashSize;
		this.setMultiPivotAmount = other.setMultiPivotAmount;
		this.setPosition = other.setPosition;
		this.setThreadAmount = other.setThreadAmount;
		this.settings = other.settings;
		this.showUci = other.showUci;
		this.showWinDrawLoss = other.showWinDrawLoss;
		this.stop = other.stop;
		this.setup = (other.setup == null)
				? null
				: Arrays.copyOf(other.setup, other.setup.length);
	}

	/**
	 * Used for creating a deep copy of the given {@code Protocol} instance.
	 *
	 * @param original the original Protocol to copy
	 * @return a deep-copied Protocol instance
	 */
	public static Protocol copyOf(Protocol original) {
		return new Protocol(original);
	}

	/**
	 * Used for counting the number of occurrences of a substring within a string.
	 *
	 * @param haystack the string to search within
	 * @param needle   the substring to count
	 * @return the number of non-overlapping occurrences of needle in haystack
	 */
	private static int countMatches(String haystack, String needle) {
		if (haystack == null || needle == null || needle.isEmpty()) {
			return 0;
		}
		int count = 0;
		int idx = 0;
		while ((idx = haystack.indexOf(needle, idx)) != -1) {
			count++;
			idx += needle.length();
		}
		return count;
	}

	/**
	 * Used for checking if the current {@code Protocol} contains usable extras for
	 * the {@code Engine}.
	 * The {@code Protocol} has usable extras if all non-essential variables are not
	 * {@code null}.
	 * If a {@code Protocol} lacks non-essential variables, it may still be usable
	 * but harder to work with.
	 *
	 * @return true if the current {@code Protocol} contains non-essential extras to
	 *         prompt the {@code Engine}
	 */
	public boolean assertExtras() {
		if (name == null) {
			return false;
		}
		if (newGame == null) {
			return false;
		}
		if (setChess960 == null || countMatches(setChess960, "%b") != 1) {
			return false;
		}
		if (setHashSize == null || countMatches(setHashSize, "%d") != 1) {
			return false;
		}
		if (setMultiPivotAmount == null || countMatches(setMultiPivotAmount, "%d") != 1) {
			return false;
		}
		if (setThreadAmount == null || countMatches(setThreadAmount, "%d") != 1) {
			return false;
		}
		if (settings == null) {
			return false;
		}
		if (showUci == null) {
			return false;
		}
		if (showWinDrawLoss == null || countMatches(showWinDrawLoss, "%b") != 1) {
			return false;
		}
		return setup != null;
	}

	/**
	 * Used for checking if the current {@code Protocol} is usable for the
	 * {@code Engine}.
	 * The {@code Protocol} is not usable if any essential variable has a
	 * {@code null} value.
	 *
	 * @return true if the current {@code Protocol} is usable for the {@code Engine}
	 */
	public boolean assertValid() {
		if (path == null) {
			return false;
		}
		if (isready == null) {
			return false;
		}
		if (readyok == null) {
			return false;
		}
		if (searchDepth == null || countMatches(searchDepth, "%d") != 1) {
			return false;
		}
		if (searchNodes == null || countMatches(searchNodes, "%d") != 1) {
			return false;
		}
		if (searchTime == null || countMatches(searchTime, "%d") != 1) {
			return false;
		}
		if (setPosition == null || countMatches(setPosition, "%s") != 1) {
			return false;
		}
		return stop != null;
	}

	/**
	 * Used for collecting all validation errors across required and optional
	 * fields.
	 *
	 * @return an array of validation error messages
	 */
	public String[] collectValidationErrors() {
		List<String> errors = new ArrayList<>();

		// non-essential
		checkField(errors, KEY_NAME, name, false, null);
		checkField(errors, KEY_NEW_GAME, newGame, false, null);
		checkField(errors, KEY_CHESS960, setChess960, false, "%b");
		checkField(errors, KEY_HASH_SIZE, setHashSize, false, "%d");
		checkField(errors, KEY_MULTI_PIVOT_AMOUNT, setMultiPivotAmount, false, "%d");
		checkField(errors, KEY_THREAD_AMOUNT, setThreadAmount, false, "%d");
		checkField(errors, KEY_SETTINGS, settings, false, null);
		checkField(errors, KEY_SHOW_UCI, showUci, false, null);
		checkField(errors, KEY_SHOW_WDL, showWinDrawLoss, false, "%b");

		// explicit array check for setup (non-essential)
		if (setup == null) {
			errors.add("'setup' should not be null");
		}

		// essential
		checkField(errors, KEY_PATH, path, true, null);
		checkField(errors, KEY_ISREADY, isready, true, null);
		checkField(errors, KEY_READYOK, readyok, true, null);
		checkField(errors, KEY_SEARCH_DEPTH, searchDepth, true, "%d");
		checkField(errors, KEY_SEARCH_NODES, searchNodes, true, "%d");
		checkField(errors, KEY_POSITION, setPosition, true, "%s");
		checkField(errors, KEY_STOP, stop, true, null);
		checkField(errors, KEY_SEARCH_TIME, searchTime, true, "%d");

		return errors.toArray(new String[0]);
	}

	/**
	 * Used for adding an error if the given field is null or does not contain
	 * exactly one placeholder.
	 *
	 * @param errors      the list to add error messages to
	 * @param fieldName   the name of the field being checked
	 * @param value       the field's value
	 * @param essential   indicates whether the field is essential
	 * @param placeholder the required placeholder to check for
	 */
	private void checkField(List<String> errors,
			String fieldName,
			String value,
			boolean essential,
			String placeholder) {
		if (value == null) {
			errors.add("'" + fieldName + "'" +
					(essential ? " cannot be null" : " should not be null"));
			return;
		}
		if (placeholder != null && countMatches(value, placeholder) != 1) {
			errors.add("'" + fieldName + "' must contain exactly one '" + placeholder + "'");
		}
	}

	/**
	 * Used for determining the file path to the {@code Engine} executable.
	 * 
	 * @return The file path to the {@code Engine} executable
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Used for determining the UCI 'isready' command.
	 * 
	 * @return The UCI 'isready' command
	 */
	public String getIsready() {
		return isready;
	}

	/**
	 * Used for determining the {@code Engine} name.
	 * 
	 * @return the {@code Engine} name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Used for determining the UCI 'newgame' command.
	 * 
	 * @return The UCI 'newgame' command
	 */
	public String getNewGame() {
		return newGame;
	}

	/**
	 * Used for determining the UCI 'readyok' response.
	 * 
	 * @return The UCI 'readyok' response
	 */
	public String getReadyok() {
		return readyok;
	}

	/**
	 * Used for determining the UCI 'go depth ' command.
	 * 
	 * @return The UCI 'go depth ' command
	 */
	public String getSearchDepth() {
		return searchDepth;
	}

	/**
	 * Used for determining the UCI 'go nodes ' command.
	 * 
	 * @return The UCI 'go nodes ' command
	 */
	public String getSearchNodes() {
		return searchNodes;
	}

	/**
	 * Used for determining the UCI 'go movetime ' command (milliseconds).
	 * 
	 * @return The UCI 'go movetime ' command
	 */
	public String getSearchTime() {
		return searchTime;
	}

	/**
	 * Used for determining the UCI command to enable Chess960.
	 * 
	 * @return The UCI command to enable Chess960
	 */
	public String getSetChess960() {
		return setChess960;
	}

	/**
	 * Used for determining the UCI command for setting the amount of usable memory.
	 * 
	 * @return the UCI command for setting the amount of usable memory
	 */
	public String getSetHashSize() {
		return setHashSize;
	}

	/**
	 * Used for determining the UCI command for setting the amount of usable memory.
	 * 
	 * @return the UCI command for setting the amount of usable memory
	 */
	public String getSetMultiPivotAmount() {
		return setMultiPivotAmount;
	}

	/**
	 * Used for determining the UCI command for setting the chess {@code Position}
	 * as a FEN.
	 * 
	 * @return The UCI command for setting the chess {@code Position} as a FEN
	 */
	public String getSetPosition() {
		return setPosition;
	}

	/**
	 * Used for determining the UCI command to set the thread amount used by the
	 * {@code Engine}.
	 * 
	 * @return The UCI command to set the thread amount used by the {@code Engine}
	 */
	public String getSetThreadAmount() {
		return setThreadAmount;
	}

	/**
	 * Used for determining the changes made to the {@code Engine}.
	 * 
	 * @return The changes made to the {@code Engine}
	 */
	public String getSettings() {
		return settings;
	}

	/**
	 * Used for retrieving the value of showUci.
	 *
	 * @return the showUci value
	 */
	public String getShowUci() {
		return showUci;
	}

	/**
	 * Used for retrieving the value of showWinDrawLoss.
	 *
	 * @return the showWinDrawLoss value
	 */
	public String getShowWinDrawLoss() {
		return showWinDrawLoss;
	}

	/**
	 * Used for retrieving the value of stop.
	 *
	 * @return the stop value
	 */
	public String getStop() {
		return stop;
	}

	/**
	 * Used for retrieving the setup commands for the engine.
	 *
	 * @return the setup commands
	 */
	public String[] getSetup() {
		return setup;
	}

	/**
	 * Used for setting the path and returning this protocol instance.
	 *
	 * @param path the path to the engine executable
	 * @return this Protocol instance
	 */
	public Protocol setPath(String path) {
		this.path = path;
		return this;
	}

	/**
	 * Used for setting the isready command and returning this protocol instance.
	 *
	 * @param isready the UCI readiness check command
	 * @return this Protocol instance
	 */
	public Protocol setIsready(String isready) {
		this.isready = isready;
		return this;
	}

	/**
	 * Used for setting the engine name and returning this protocol instance.
	 *
	 * @param name the name of the engine
	 * @return this Protocol instance
	 */
	public Protocol setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Used for setting the newGame command and returning this protocol instance.
	 *
	 * @param newGame the UCI new game command
	 * @return this Protocol instance
	 */
	public Protocol setNewGame(String newGame) {
		this.newGame = newGame;
		return this;
	}

	/**
	 * Used for setting the readyok response and returning this protocol instance.
	 *
	 * @param readyok the UCI ready response
	 * @return this Protocol instance
	 */
	public Protocol setReadyok(String readyok) {
		this.readyok = readyok;
		return this;
	}

	/**
	 * Used for setting the searchDepth command and returning this protocol
	 * instance.
	 *
	 * @param searchDepth the UCI search depth command
	 * @return this Protocol instance
	 */
	public Protocol setSearchDepth(String searchDepth) {
		this.searchDepth = searchDepth;
		return this;
	}

	/**
	 * Used for setting the searchNodes command and returning this protocol
	 * instance.
	 *
	 * @param searchNodes the UCI search nodes command
	 * @return this Protocol instance
	 */
	public Protocol setSearchNodes(String searchNodes) {
		this.searchNodes = searchNodes;
		return this;
	}

	/**
	 * Used for setting the searchTime command and returning this protocol instance.
	 * 
	 * @param searchTime the UCI fixed-time search command (must contain exactly one
	 *                   %d)
	 * @return this Protocol instance
	 */
	public Protocol setSearchTime(String searchTime) {
		this.searchTime = searchTime;
		return this;
	}

	/**
	 * Used for setting the setChess960 command and returning this protocol
	 * instance.
	 *
	 * @param setChess960 the UCI Chess960 mode command
	 * @return this Protocol instance
	 */
	public Protocol setSetChess960(String setChess960) {
		this.setChess960 = setChess960;
		return this;
	}

	/**
	 * Used for setting the setHashSize command and returning this protocol
	 * instance.
	 *
	 * @param setHashSize the UCI hash size setting command
	 * @return this Protocol instance
	 */
	public Protocol setSetHashSize(String setHashSize) {
		this.setHashSize = setHashSize;
		return this;
	}

	/**
	 * Used for setting the setMultiPivotAmount command and returning this protocol
	 * instance.
	 *
	 * @param setMultiPivotAmount the UCI multipv setting command
	 * @return this Protocol instance
	 */
	public Protocol setSetMultiPivotAmount(String setMultiPivotAmount) {
		this.setMultiPivotAmount = setMultiPivotAmount;
		return this;
	}

	/**
	 * Used for setting the setPosition command and returning this protocol
	 * instance.
	 *
	 * @param setPosition the UCI set position command
	 * @return this Protocol instance
	 */
	public Protocol setSetPosition(String setPosition) {
		this.setPosition = setPosition;
		return this;
	}

	/**
	 * Used for setting the setThreadAmount command and returning this protocol
	 * instance.
	 *
	 * @param setThreadAmount the UCI thread amount setting command
	 * @return this Protocol instance
	 */
	public Protocol setSetThreadAmount(String setThreadAmount) {
		this.setThreadAmount = setThreadAmount;
		return this;
	}

	/**
	 * Used for setting the engine settings and returning this protocol instance.
	 *
	 * @param engineSettings the custom engine settings
	 * @return this Protocol instance
	 */
	public Protocol setSettings(String engineSettings) {
		this.settings = engineSettings;
		return this;
	}

	/**
	 * Used for setting the showUci command and returning this protocol instance.
	 *
	 * @param showUci the UCI show options command
	 * @return this Protocol instance
	 */
	public Protocol setShowUci(String showUci) {
		this.showUci = showUci;
		return this;
	}

	/**
	 * Used for setting the showWinDrawLoss command and returning this protocol
	 * instance.
	 *
	 * @param showWinDrawLoss the UCI ShowWDL setting command
	 * @return this Protocol instance
	 */
	public Protocol setShowWinDrawLoss(String showWinDrawLoss) {
		this.showWinDrawLoss = showWinDrawLoss;
		return this;
	}

	/**
	 * Used for setting the stop command and returning this protocol instance.
	 *
	 * @param stop the UCI stop command
	 * @return this Protocol instance
	 */
	public Protocol setStop(String stop) {
		this.stop = stop;
		return this;
	}

	/**
	 * Used for setting the engine setup commands and returning this protocol
	 * instance.
	 *
	 * @param setup the commands to initialize the engine
	 * @return this Protocol instance
	 */
	public Protocol setSetup(String[] setup) {
		this.setup = setup;
		return this;
	}

	/**
	 * Used for generating a TOML representation with aligned keys, values, and
	 * comments.
	 *
	 * @return the formatted TOML string
	 */
	public String toTOML() {
		StringBuilder sb = new StringBuilder();

		// Header
		sb.append("#\n");
		sb.append("# Chess-Engine UCI Protocol configuration for '").append(name).append("'\n");
		sb.append("# (created " + Dates.getTimestamp() + ")\n");
		sb.append("#\n\n");
		sb.append("# %d → integer      %s → string      %b → boolean\n");
		sb.append("# Can only ever contain up to one %d, %s or %b\n\n");

		// Collect entries: key constant, value, comment
		List<String[]> entries = Arrays.asList(
				new String[] { KEY_PATH, path, "Used for identifying the key for the engine executable path." },
				new String[] { KEY_NAME, name, "Used for identifying the key for the engine name. (not essential)" },
				new String[] { KEY_SETTINGS, settings,
						"Used for identifying the key for the engine's configuration overrides. (not essential)" },
				new String[] { KEY_ISREADY, isready, "Used for identifying the key for the UCI isready command." },
				new String[] { KEY_READYOK, readyok, "Used for identifying the key for the UCI readyok response." },
				new String[] { KEY_SEARCH_DEPTH, searchDepth,
						"Used for identifying the key for the UCI search depth command." },
				new String[] { KEY_SEARCH_NODES, searchNodes,
						"Used for identifying the key for the UCI search nodes command." },
				new String[] { KEY_SEARCH_TIME, searchTime,
						"Used for identifying the key for the UCI fixed-time search command (milliseconds). (not essential)" },
				new String[] { KEY_POSITION, setPosition,
						"Used for identifying the key for the UCI position command." },
				new String[] { KEY_STOP, stop, "Used for identifying the key for the UCI stop command." },
				new String[] { KEY_NEW_GAME, newGame,
						"Used for identifying the key for the UCI new game command. (not essential)" },
				new String[] { KEY_CHESS960, setChess960,
						"Used for identifying the key for enabling or disabling Chess960. (not essential)" },
				new String[] { KEY_HASH_SIZE, setHashSize,
						"Used for identifying the key for setting the transposition table size. (not essential)" },
				new String[] { KEY_MULTI_PIVOT_AMOUNT, setMultiPivotAmount,
						"Used for identifying the key for setting the number of multi-pivot variations. (not essential)" },
				new String[] { KEY_THREAD_AMOUNT, setThreadAmount,
						"Used for identifying the key for setting the thread count. (not essential)" },
				new String[] { KEY_SHOW_UCI, showUci,
						"Used for identifying the key for displaying UCI options. (not essential)" },
				new String[] { KEY_SHOW_WDL, showWinDrawLoss,
						"Used for identifying the key for enabling or disabling WDL stats. (not essential)" });

		// Determine max key length
		int maxKeyLen = 0;
		for (String[] e : entries) {
			maxKeyLen = Math.max(maxKeyLen, e[0].length());
		}

		// Build prefixes and find max prefix length
		List<String> prefixes = new ArrayList<>();
		int maxPrefixLen = 0;
		for (String[] e : entries) {
			String key = e[0];
			String val = e[1];
			// pad between key and = for alignment (min 3 spaces + dynamic)
			int padKey = (maxKeyLen - key.length()) + 3;
			StringBuilder pfx = new StringBuilder();
			pfx.append(key);
			for (int i = 0; i < padKey; i++)
				pfx.append(' ');
			pfx.append("= \"").append(val).append("\"");
			String prefix = pfx.toString();
			prefixes.add(prefix);
			maxPrefixLen = Math.max(maxPrefixLen, prefix.length());
		}

		// Append entries with aligned comments
		for (int i = 0; i < entries.size(); i++) {
			String prefix = prefixes.get(i);
			sb.append(prefix);
			int padComment = (maxPrefixLen - prefix.length()) + 3;
			for (int j = 0; j < padComment; j++)
				sb.append(' ');
			sb.append("# ").append(entries.get(i)[2]).append("\n");
		}

		// Setup section
		sb.append("\n# The inputs that will be fed into the Engine once it is ready (Optional)\n");
		sb.append(KEY_SETUP).append(" = [\n");
		for (int i = 0; i < setup.length; i++) {
			sb.append("   \"").append(setup[i]).append("\"");
			if (i + 1 < setup.length) {
				sb.append(",\n");
			} else {
				sb.append("\n");
			}
		}
		sb.append("]\n");

		return sb.toString();
	}

	/**
	 * Used for creating a Protocol with default values.
	 */
	public Protocol() {

	}

}
