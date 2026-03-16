package chess.nn.t5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SentencePiece unigram tokenizer used by T5.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Tokenizer {

  /**
   * Vocabulary pieces from the export.
   */
  private final String[] pieces;
  
  /**
   * Unigram scores aligned with {@code pieces}.
   */
  private final float[] scores;
  
  /**
   * Token id used when no piece matches.
   */
  private final int unkId;
  
  /**
   * Quick lookup from first codepoint to candidate ids.
   */
  private final Map<Integer, List<Integer>> prefixMap;

  /**
   * Creates a tokenizer with the exported vocabulary.
   *
   * @param pieces piece strings
   * @param scores piece scores
   * @param unkId unknown piece id
   */
  public Tokenizer(String[] pieces, float[] scores, int unkId) {
    this.pieces = pieces;
    this.scores = scores;
    this.unkId = unkId;
    this.prefixMap = buildPrefixMap(pieces);
    float min = Float.MAX_VALUE;
    for (float score : scores) {
      if (score < min) {
        min = score;
      }
    }
    if (scores[unkId] >= min) {
      scores[unkId] = min - 10f;
    }
  }

  /**
   * Encodes text into token ids using a Viterbi search.
   *
   * @param text input text
   * @return token ids
   */
  public List<Integer> encode(String text) {
    String normalized = normalizeText(text);
    int length = normalized.length();
    float[] bestScore = initScores(length);
    int[] bestId = initInts(length);
    int[] bestPrev = initInts(length);
    bestScore[0] = 0f;

    for (int i = 0; i < length; i++) {
      if (bestScore[i] == Float.NEGATIVE_INFINITY) {
        continue;
      }
      int firstChar = normalized.charAt(i);
      List<Integer> candidates = prefixMap.get(firstChar);
      if (candidates != null) {
        updateBestFromCandidates(normalized, i, candidates, bestScore, bestId, bestPrev);
      }
      applyUnknownFallback(i, bestScore, bestId, bestPrev);
    }

    return backtrackTokens(bestId, bestPrev, length);
  }

  /**
   * Normalizes whitespace and adds leading underscores.
   *
   * @param text original input
   * @return normalized string
   */
  private String normalizeText(String text) {
    String collapsed = text.replaceAll("\\s+", " ").trim();
    return "▁" + collapsed.replace(" ", "▁");
  }

  /**
   * Allocates the best score buffer initialized to -inf.
   *
   * @param length text length
   * @return score array
   */
  private float[] initScores(int length) {
    float[] bestScore = new float[length + 1];
    for (int i = 0; i <= length; i++) {
      bestScore[i] = Float.NEGATIVE_INFINITY;
    }
    return bestScore;
  }

  /**
   * Allocates and initializes index buffers to -1.
   *
   * @param length text length
   * @return index array
   */
  private int[] initInts(int length) {
    int[] values = new int[length + 1];
    for (int i = 0; i <= length; i++) {
      values[i] = -1;
    }
    return values;
  }

  /**
   * Updates Viterbi scores using candidate pieces.
   *
   * @param normalized normalized text
   * @param index current position
   * @param candidates piece ids sharing the prefix
   * @param bestScore best scores buffer
   * @param bestId best id buffer
   * @param bestPrev best predecessor buffer
   */
  private void updateBestFromCandidates(String normalized, int index, List<Integer> candidates, float[] bestScore, int[] bestId, int[] bestPrev) {
    for (int id : candidates) {
      String piece = pieces[id];
      if (normalized.startsWith(piece, index)) {
        int next = index + piece.length();
        float score = bestScore[index] + scores[id];
        if (score > bestScore[next]) {
          bestScore[next] = score;
          bestId[next] = id;
          bestPrev[next] = index;
        }
      }
    }
  }

  /**
   * Applies the unknown-piece fallback to keep coverage.
   *
   * @param index current position
   * @param bestScore best scores buffer
   * @param bestId best id buffer
   * @param bestPrev predecessor buffer
   */
  private void applyUnknownFallback(int index, float[] bestScore, int[] bestId, int[] bestPrev) {
    int next = index + 1;
    float score = bestScore[index] + scores[unkId];
    if (score > bestScore[next]) {
      bestScore[next] = score;
      bestId[next] = unkId;
      bestPrev[next] = index;
    }
  }

  /**
   * Reconstructs the token path from the Viterbi tables.
   *
   * @param bestId best ids per position
   * @param bestPrev predecessors
   * @param length normalized length
   * @return decoded token list
   */
  private List<Integer> backtrackTokens(int[] bestId, int[] bestPrev, int length) {
    List<Integer> tokens = new ArrayList<>();
    int idx = length;
    while (idx > 0) {
      int id = bestId[idx];
      if (id < 0 || bestPrev[idx] < 0) {
        tokens.add(unkId);
        break;
      }
      tokens.add(0, id);
      idx = bestPrev[idx];
    }
    return tokens;
  }

  /**
   * Decodes token ids into text.
   *
   * @param ids token ids
   * @return decoded text
   */
  public String decode(List<Integer> ids) {
    StringBuilder sb = new StringBuilder();
    for (int id : ids) {
      if (id < 0 || id >= pieces.length) {
        continue;
      }
      sb.append(pieces[id]);
    }
    return sb.toString().replace("▁", " ").trim();
  }

  /**
   * Checks if any piece starts with the given character.
   *
   * @param ch character to test
   * @return true if at least one piece matches the prefix
   */
  public boolean hasPrefix(char ch) {
    return prefixMap.containsKey((int) ch);
  }

  /**
   * Returns the score for a piece id.
   *
   * @param id piece id
   * @return score
   */
  public float score(int id) {
    return scores[id];
  }

  /**
   * Builds the prefix map used for efficient candidate lookup.
   *
   * @param pieces vocabulary pieces
   * @return map from codepoint to candidate ids
   */
  private Map<Integer, List<Integer>> buildPrefixMap(String[] pieces) {
    Map<Integer, List<Integer>> map = new HashMap<>();
    for (int i = 0; i < pieces.length; i++) {
      if (pieces[i].isEmpty()) {
        continue;
      }
      int codepoint = pieces[i].codePointAt(0);
      map.computeIfAbsent(codepoint, key -> new ArrayList<>()).add(i);
    }
    return map;
  }
}
