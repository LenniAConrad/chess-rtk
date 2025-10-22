package chess.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

import chess.core.Position;
import chess.model.Record;
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
  
}
