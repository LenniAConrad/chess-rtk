package testing;



/**
 * Render-ready puzzle snapshot move text.
 *
 * @param movetext move-numbered SAN text
 * @param sourceTree whether the text came from an original source tree
 */
record PuzzleRatingDetail(String movetext, boolean sourceTree) {
}
