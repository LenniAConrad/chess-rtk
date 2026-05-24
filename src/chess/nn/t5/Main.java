package chess.nn.t5;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI entry point for the no-deps T5 evaluator.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Main {
  /**
   * Runs the CLI.
   *
   * @param args command-line arguments
   * @throws IOException on input or model read errors
   */
  public static void main(String[] args) throws IOException {
    System.out.println("Starting T5 CLI");
    System.out.flush();
    CliOptions options = parseOptions(args);
    if (options.modelPath == null) {
      System.err.println("Missing --model path");
      return;
    }

    String input = readInput(options.inputPath);
    TagInputParser parser = new TagInputParser();
    List<String> tags = parser.parse(input);
    if (tags.isEmpty()) {
      System.out.println("No tag information provided.");
      return;
    }

    String prompt = buildPrompt(tags);
    Model model = BinLoader.load(options.modelPath);
    try (Runner runner = new Runner(model)) {
      if (options.debug) {
        System.err.println("Prompt length=" + prompt.length());
        System.err.println("Tokens=" + model.tokenizer.encode(prompt).size());
        List<Integer> outIds = runner.generateIds(prompt, options.maxNew);
        System.err.println("Output ids=" + outIds);
        Tensor logits = runner.decodeOnce(List.of(model.config.decoderStartId), runner.encodePrompt(prompt));
        printTop(logits, 5);
      }
      String output = runner.generate(prompt, options.maxNew);
      System.out.println(output);
      System.out.flush();
    }
  }

  /**
   * Parses CLI options.
   *
   * @param args command-line arguments
   * @return parsed options
   */
  private static CliOptions parseOptions(String[] args) {
    CliOptions options = new CliOptions();
    int index = 0;
    while (index < args.length) {
      index = applyOption(args, index, options);
    }
    return options;
  }

  /**
   * Applies one CLI option and returns the next unread argument index.
   *
   * @param args command-line arguments
   * @param index current argument index
   * @param options mutable target options
   * @return next unread argument index
   */
  private static int applyOption(String[] args, int index, CliOptions options) {
    String arg = args[index];
    if ("--model".equals(arg)) {
      String value = optionValue(args, index);
      if (value != null) {
        options.modelPath = value;
        return index + 2;
      }
      return index + 1;
    }
    if ("--input".equals(arg)) {
      String value = optionValue(args, index);
      if (value != null) {
        options.inputPath = value;
        return index + 2;
      }
      return index + 1;
    }
    if ("--max-new".equals(arg)) {
      String value = optionValue(args, index);
      if (value != null) {
        options.maxNew = Integer.parseInt(value);
        return index + 2;
      }
      return index + 1;
    }
    if ("--debug".equals(arg)) {
      options.debug = true;
    }
    return index + 1;
  }

  /**
   * Returns the argument value following one CLI option, if present.
   *
   * @param args command-line arguments
   * @param index option index
   * @return following argument, or {@code null} when absent
   */
  private static String optionValue(String[] args, int index) {
    return index + 1 < args.length ? args[index + 1] : null;
  }

  /**
   * Builds a summarization prompt from tag lines.
   *
   * @param tags parsed tags
   * @return prompt text
   */
  private static String buildPrompt(List<String> tags) {
    StringBuilder sb = new StringBuilder();
    sb.append("summarize tags:\nTags:\n");
    for (String tag : tags) {
      sb.append("- ").append(tag).append("\n");
    }
    sb.append("\nReturn 2-4 sentences.\nSummary:");
    return sb.toString();
  }

  /**
   * Reads input from stdin or a fallback file.
   *
   * @param inputPath optional file path
   * @return input text
   * @throws IOException on file read errors
   */
  private static String readInput(String inputPath) throws IOException {
    if (inputPath != null) {
      return Files.readString(Path.of(inputPath));
    }
    byte[] bytes = System.in.readAllBytes();
    if (bytes.length > 0) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
    return "";
  }

  /**
   * Prints top-k logits for debugging.
   *
   * @param logits logits tensor
   * @param k number of entries to print
   */
  private static void printTop(Tensor logits, int k) {
    int vocab = logits.shape[1];
    int offset = (logits.shape[0] - 1) * vocab;
    float[] vals = logits.data;
    int[] bestIdx = new int[k];
    float[] bestVal = new float[k];
    for (int i = 0; i < k; i++) {
      bestVal[i] = -Float.MAX_VALUE;
      bestIdx[i] = -1;
    }
    for (int i = 0; i < vocab; i++) {
      float v = vals[offset + i];
      for (int j = 0; j < k; j++) {
        if (v > bestVal[j]) {
          for (int m = k - 1; m > j; m--) {
            bestVal[m] = bestVal[m - 1];
            bestIdx[m] = bestIdx[m - 1];
          }
          bestVal[j] = v;
          bestIdx[j] = i;
          break;
        }
      }
    }
    System.err.print("Top logits: ");
    for (int i = 0; i < k; i++) {
      System.err.print(bestIdx[i] + ":" + bestVal[i] + " ");
    }
    System.err.println();
  }

  /**
   * Mutable CLI options.
   */
  private static final class CliOptions {
    /**
     * Model path.
     */
    private String modelPath;

    /**
     * Optional input path.
     */
    private String inputPath;

    /**
     * Maximum number of generated tokens.
     */
    private int maxNew = 128;

    /**
     * Whether debug output is enabled.
     */
    private boolean debug;
  }
}
