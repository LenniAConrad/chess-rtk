package testing;



/**
 * Integer row field accessor.
 */
interface PuzzleRatingIntField {
    /**
     * Returns the integer value for a CSV row.
     *
     * @param row source row
     * @return integer value
     */
    int value(PuzzleRatingRow row);
}
