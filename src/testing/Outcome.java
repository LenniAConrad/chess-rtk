package testing;




/**
 * Coarse outcome buckets.
 */
enum Outcome {
    /**
     * The side to move is clearly better.
     */
    WINNING("winning"),

    /**
     * The side to move is clearly worse.
     */
    LOSING("losing"),

    /**
     * The score is close to equality.
     */
    DRAWISH("drawish"),

    /**
     * The score is neither equal nor clearly decisive.
     */
    UNCLEAR("unclear");

    /**
     * CSV-safe label.
     */
    private final String label;

    /**
     * Outcome.
     * @param label label text
     */
    Outcome(String label) {
        this.label = label;
    }

    /**
     * Returns the CSV-safe label.
     * @return label result
     */
    String label() {
        return label;
    }
}
