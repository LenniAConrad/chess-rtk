package application;

import static application.cli.Constants.CMD_BOOK;
import static application.cli.Constants.CMD_CLEAN;
import static application.cli.Constants.CMD_CONFIG;
import static application.cli.Constants.CMD_DOCTOR;
import static application.cli.Constants.CMD_ENGINE;
import static application.cli.Constants.CMD_FEN;
import static application.cli.Constants.CMD_HELP;
import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.CMD_GUI;
import static application.cli.Constants.CMD_GUI_NEXT;
import static application.cli.Constants.CMD_GUI_WEB;
import static application.cli.Constants.CMD_MOVE;
import static application.cli.Constants.CMD_PUZZLE;
import static application.cli.Constants.CMD_RECORD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import application.cli.command.BookGroupCommand;
import application.cli.command.CleanCommand;
import application.cli.command.ConfigCommand;
import application.cli.command.DoctorCommand;
import application.cli.command.FenGroupCommand;
import application.gui.GuiCommand;
import application.gui.GuiNextCommand;
import application.gui.GuiWebCommand;
import application.cli.command.HelpCommand;
import application.cli.command.EngineGroupCommand;
import application.cli.command.MoveGroupCommand;
import application.cli.command.PuzzleGroupCommand;
import application.cli.command.RecordGroupCommand;
import utility.Argv;

/**
 * Used for providing the CLI entry point and dispatching subcommands.
 *
 * <p>
 * The dispatcher exposes the current top-level command families:
 * {@code record}, {@code fen}, {@code move}, {@code engine}, {@code book},
 * {@code puzzle}, plus operational commands such as {@code gui},
 * {@code config}, {@code doctor}, {@code clean}, and {@code help}.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Main {

	/**
	 * Shared subcommands constant.
	 */
	private static final Map<String, Consumer<Argv>> SUBCOMMANDS = buildSubcommands();

	/**
	 * Handles build subcommands.
	 * @return computed value
	 */
	private static Map<String, Consumer<Argv>> buildSubcommands() {
		Map<String, Consumer<Argv>> map = new HashMap<>(64);
		map.put(CMD_RECORD, RecordGroupCommand::runRecord);
		map.put(CMD_FEN, FenGroupCommand::runFen);
		map.put(CMD_MOVE, MoveGroupCommand::runMove);
		map.put(CMD_ENGINE, EngineGroupCommand::runEngine);
		map.put(CMD_BOOK, BookGroupCommand::runBook);
		map.put(CMD_PUZZLE, PuzzleGroupCommand::runPuzzle);
		map.put(CMD_GUI, GuiCommand::runGui);
		map.put(CMD_GUI_WEB, GuiWebCommand::runGuiWeb);
		map.put(CMD_GUI_NEXT, GuiNextCommand::runGuiNext);
		map.put(CMD_CLEAN, CleanCommand::runClean);
		map.put(CMD_DOCTOR, DoctorCommand::runDoctor);
		map.put(CMD_CONFIG, ConfigCommand::runConfig);
		map.put(CMD_HELP, HelpCommand::runHelp);
		map.put(CMD_HELP_SHORT, HelpCommand::runHelp);
		map.put(CMD_HELP_LONG, HelpCommand::runHelp);
		return map;
	}

	/**
	 * Utility class; prevent instantiation.
	 */
	private Main() {
		// utility
	}

	/**
	 * Used for parsing top-level CLI arguments and delegating to a subcommand
	 * handler.
	 *
	 * <p>
	 * Behavior:
	 * </p>
	 * <ul>
	 * <li>Attempts to read the first positional argument as the subcommand.</li>
	 * <li>Delegates remaining positionals to the corresponding command handler.</li>
	 * <li>On unknown subcommands, prints help and exits with status {@code 2}.</li>
	 * </ul>
	 *
	 * @param argv raw command-line arguments; first positional must be a valid
	 *             subcommand
	 */
	public static void main(String[] argv) {
		Argv a = new Argv(argv);

		List<String> head = a.positionals();

		a.ensureConsumed();

		if (head.isEmpty()) {
			HelpCommand.helpSummary();
			return;
		}

		String sub = head.get(0);
		String[] tail = head.subList(1, head.size()).toArray(new String[0]);
		Argv b = new Argv(tail);

		Consumer<Argv> handler = SUBCOMMANDS.get(sub);
		if (handler != null) {
			handler.accept(b);
			return;
		}
		System.err.println("Unknown command: " + sub);
		HelpCommand.helpSummary();
		System.exit(2);
	}
}
