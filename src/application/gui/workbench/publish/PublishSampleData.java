package application.gui.workbench.publish;

import java.util.List;

/**
 * Brief, real sample content for the publishing preview.
 *
 * <p>When the workbench has no live data to publish (no input file selected,
 * empty batch, no game), the preview still needs something concrete to draw so
 * the reader can tell a study from a puzzle collection from a cover. Rather than
 * invent grey placeholder bars, this bundles a handful of genuine positions and
 * captions lifted from the on-disk sample book ({@code dump/books/art-of-chess})
 * and the rated puzzle exports ({@code dump/puzzles}). The data is intentionally
 * tiny — just enough to populate one preview page per task.</p>
 */
public final class PublishSampleData {

    /**
     * Sample book title used when no study/collection title is supplied.
     */
    public static final String SAMPLE_TITLE = "Art of Chess Puzzles";

    /**
     * Sample subtitle.
     */
    public static final String SAMPLE_SUBTITLE = "Legacy Opening Selection";

    /**
     * Sample author.
     */
    public static final String SAMPLE_AUTHOR = "Lennart A. Conrad";

    private PublishSampleData() {
    }

    /**
     * One previewable item: a position plus a short caption and detail line.
     *
     * @param fen full FEN of the diagram position
     * @param label short caption (puzzle/composition title)
     * @param detail supporting line (solution hint, rating, or move)
     */
    public record SampleItem(String fen, String label, String detail) {
    }

    /**
     * Study/composition sample, taken verbatim from the legacy Art of Chess book.
     *
     * @return ordered study items
     */
    public static List<SampleItem> studyItems() {
        return List.of(
                new SampleItem("r4r1k/ppp2p2/4bN1p/4PPpQ/3q4/8/PP3PPP/R1K2B1R b - - 0 1",
                        "Legacy Opening 1", "1… Qc5+ wins four points of material"),
                new SampleItem("r1k3nr/pp4pp/3bqp2/1Q6/4p3/4B3/PPP3PP/3R1RK1 w - - 0 1",
                        "Legacy Opening 2", "1. Bf4 deflects the dark-squared bishop"),
                new SampleItem("rn5r/3k2pp/p1qbRn2/2p3N1/Q4P2/2PP2P1/P6P/1R4K1 w - - 0 1",
                        "Legacy Opening 3", "1. Rb7+ and the attack breaks through"));
    }

    /**
     * Rated puzzle sample, taken from the engine puzzle exports.
     *
     * @return ordered puzzle items
     */
    public static List<SampleItem> puzzleItems() {
        return List.of(
                new SampleItem("3r2Q1/1bqk1p2/p3p3/1p6/8/P1N5/1Pn3PP/5R1K w - - 2 24",
                        "Mate in 2", "rating 1642 · medium"),
                new SampleItem("r1bk3r/p1p2Qp1/2pbp2p/8/7q/8/PB3PP1/RN1R2K1 w - - 2 16",
                        "Win the queen", "rating 1697 · medium"),
                new SampleItem("r7/pkp5/2p1QB1p/1b6/5b2/2Nq4/P4P2/1RKR4 w - - 5 26",
                        "Crushing attack", "rating 1515 · medium"),
                new SampleItem("1r6/1P6/P5pp/1K1k4/8/3pp3/3R3P/8 w - - 0 52",
                        "Promotion race", "rating 1050 · very easy"),
                new SampleItem("Q6k/6p1/5p1p/2p1p3/pq4P1/5P1K/8/8 b - - 2 56",
                        "Back-rank finish", "rating 1258 · easy"),
                new SampleItem("rnbqkbnr/ppp4p/6B1/3p1p2/4p3/4P3/PPPPNPPP/RNBQK2R b KQkq - 0 6",
                        "Opening trap", "rating 990 · very easy"));
    }
}
