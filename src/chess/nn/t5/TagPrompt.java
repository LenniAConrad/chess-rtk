package chess.nn.t5;

import java.util.List;

/**
 * Builds tag-oriented prompts for T5 inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class TagPrompt {

     /**
     * Creates a new tag prompt instance.
     */
     private TagPrompt() {
        // utility
    }

     /**
     * Handles build position prompt.
     * @param tags tags
     * @return computed value
     */
     public static String buildPositionPrompt(List<String> tags) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("TASK: puzzle_commentary\n");
        sb.append("Write a single-paragraph chess commentary.\n");
        sb.append("If the line includes multiple variations or a long forcing sequence, aim for 220-320 words; otherwise 120-180.\n");
        sb.append("Foundation first: material + king safety + top tactical motif.\n");
        sb.append("Then explain the main forcing idea or threat.\n");
        sb.append("Do not invent moves; only mention moves that appear in tags.\n");
        sb.append("End with qualitative evaluation (prefer META: eval_bucket).\n");
        sb.append("Never mention tags, PGN, prompts, or input format.\n");
        sb.append("Do not use meta phrases like \"as described\", \"provided\", or \"according to the input\".\n");
        sb.append("TAGS:\n");
        for (String tag : tags) {
            sb.append("- ").append(tag).append("\n");
        }
        sb.append("OUTPUT:");
        return sb.toString();
    }
}
