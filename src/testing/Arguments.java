package testing;

import static testing.PuzzleVolatilityReport.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import application.Config;
import chess.core.Move;
import chess.core.Position;
import chess.struct.Record;
import chess.uci.Analysis;
import utility.Json;

/**
 * Shared parsed command-line arguments for puzzle report tools.
 *
 * @param inputs input record files or directories
 * @param prefix output path prefix
 * @param maxPuzzles maximum puzzle rows to process
 */
record Arguments(List<Path> inputs, Path prefix, int maxPuzzles) {
}
