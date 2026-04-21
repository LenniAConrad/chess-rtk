package chess.book.render;

/**
 * Stores the outer dimensions reserved for a vector drop shadow.
 *
 * @param totalWidth total shadow-inclusive width
 * @param totalHeight total shadow-inclusive height
 * @param blur shadow blur reserve
 * @since 2026
 * @author Lennart A. Conrad
 */
record ShadowBoxGeometry(double totalWidth, double totalHeight, double blur) {
}
