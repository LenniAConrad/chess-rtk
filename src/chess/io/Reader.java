package chess.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

import chess.core.Position;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.struct.Record;
import utility.Json;

/**
 * Provides utility methods for reading and parsing chess game data from files.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Reader {

  /**
   * Used for preventing instantiation of this utility class.
   */
  private Reader() {
    // non-instantiable
  }

  /**
   * Reads a JSON file containing a top-level JSON array of objects and parses
   * each object into a Record instance.
   * 
   * @param path the path to the JSON file
   * @return a list of Record instances parsed from the file, or an empty list if
   *         the file doesn't exist or is empty
   * @throws IOException if an I/O error occurs while reading the file
   */
  public static List<Record> readJsonFile(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return List.of();
    }
    String s = Files.readString(path);
    List<Record> out = new ArrayList<>();
    List<String> parts = Json.splitTopLevelObjects(s);
    for (String obj : parts) {
      try {
        Record rec = Record.fromJson(obj);
        if (rec != null) {
          out.add(rec);
        }
      } catch (IllegalArgumentException ignored) {
        // Skip malformed records
      }
    }
    return out;
  }

  /**
   * Reads a UTF-8 text file where each non-empty line is a FEN string.
   * Lines starting with '#' or '//' are treated as comments and skipped.
   *
   * <p>The parser tolerates stray whitespace and ignores blank/comment lines
   * so that auxiliary files can include human-readable notes in addition to FENs.</p>
   *
   * @param path the path to the text file
   * @return an immutable list of FEN strings in file order; empty if the file is missing/empty
   * @throws IOException if an I/O error occurs while reading
   */
  public static List<String> readFenList(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return List.of();
    }
    try (Stream<String> lines = Files.lines(path)) {
      return lines
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .filter(s -> !(s.startsWith("#") || s.startsWith("//")))
          .toList();
    }
  }

  /**
   * Reads a text file where each non-empty line may contain one or two FENs.
   * <ul>
   * <li>If exactly one FEN is found on a line, it becomes the position and the
   * parent is left unset.</li>
   * <li>If two or more FENs are found on a line, the first is treated as the
   * parent and the second as the position.</li>
   * </ul>
   * Lines starting with {@code #} or {@code //} are ignored as comments.
   *
   * @param path text file with FENs
   * @return records with position and optional parent
   * @throws IOException on read errors
   */
  public static List<Record> readPositionRecords(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return List.of();
    }

    List<Record> records = new ArrayList<>();
    try (Stream<String> lines = Files.lines(path)) {
      lines.map(String::trim)
          .filter(s -> !s.isEmpty())
          .filter(s -> !(s.startsWith("#") || s.startsWith("//")))
          .forEach(line -> {
            List<String> fens = extractFens(line);
            if (fens.isEmpty()) {
              return;
            }
            try {
              if (fens.size() == 1) {
                Position pos = new Position(fens.get(0));
                records.add(new Record().withPosition(pos));
              } else {
                Position parent = new Position(fens.get(0));
                Position pos = new Position(fens.get(1));
                records.add(new Record().withParent(parent).withPosition(pos));
              }
            } catch (IllegalArgumentException ignored) {
              // skip malformed fen sequences on this line
            }
          });
    }
    return records;
  }

  /**
   * Extracts valid FENs from a whitespace-separated line.
   * Returns only windows that parse successfully as full FEN strings.
   *
   * @param line input line to scan
   * @return list of detected FEN strings (possibly empty)
   */
  private static List<String> extractFens(String line) {
    if (line == null || line.isEmpty()) {
      return List.of();
    }
    String[] tokens = line.split("\\s+");
    List<String> fens = new ArrayList<>();
    for (int i = 0; i < tokens.length; i++) {
      int[] lengths = { 6, 5, 4 };
      boolean foundFen = false;
      for (int len : lengths) {
        if (!foundFen && i + len <= tokens.length) {
          String fen = String.join(" ", Arrays.copyOfRange(tokens, i, i + len));
          try {
            new Position(fen);
            fens.add(fen);
            foundFen = true;
          } catch (IllegalArgumentException ignored) {
            // not a fen starting at this token window
          }
        }
      }
    }
    return fens;
  }

  /**
   * Reads a FEN file (see {@link #readFenList(Path)}) and returns a stack-like deque.
   * By default, the "top" of the stack is the **first line** from the file, so a
   * {@code pop()} gives you the earliest task first. You can still {@code push(...)}
   * new tasks on top during processing.
   *
   * @param path the path to the FEN list file
   * @return a mutable {@code ArrayDeque<String>} with FENs ready to {@code pop()}, {@code push()}, etc.
   * @throws IOException if an I/O error occurs while reading
   */
  public static Deque<String> readFenStack(Path path) throws IOException {
    List<String> fen = readFenList(path);
    ArrayDeque<String> stack = new ArrayDeque<>(fen.size());
    for (int i = fen.size() - 1; i >= 0; i--) {
      stack.push(fen.get(i));
    }
    return stack;
  }

  /**
   * Reads a FEN list file and returns parsed {@link Position} instances.
   * Invalid FENs are skipped without failing the entire read.
   *
   * @param path path to the FEN list file
   * @return list of parsed positions
   * @throws IOException if an I/O error occurs while reading
   */
  public static List<Position> readPositionList(Path path) throws IOException {
    List<String> fenList = readFenList(path);
    List<Position> positions = new ArrayList<>(fenList.size());
    for (String fen : fenList) {
      try {
        positions.add(new Position(fen));
      } catch (IllegalArgumentException ignored) {
        // skip invalid FENs
      }
    }
    return positions;
  }

  /**
   * Reads one or more PGN games from the given file.
   *
   * @param path PGN file path
   * @return parsed games (possibly empty)
   * @throws IOException if reading fails
   */
  public static List<Game> readPgn(Path path) throws IOException {
    return Pgn.read(path);
  }
  
}
