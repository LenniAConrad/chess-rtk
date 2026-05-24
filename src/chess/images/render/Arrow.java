package chess.images.render;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Stroke;

import chess.core.Move;

/**
 * Immutable description of an arrow overlay between two squares.
 *
 * @since 2024
 * @author Lennart A. Conrad
 */
public final class Arrow {
	
	/**
	 * Encoded move that defines the start and end squares.
	 */
	final short move;
	
	/**
	 * Arrow head height in pixels.
	 */
	final int arrowHeadHeight;
	
	/**
	 * Amount trimmed from the start of the segment in pixels.
	 */
	final int startShortener;
	
	/**
	 * Amount trimmed from the end of the segment in pixels.
	 */
	final int endShortener;
	
	/**
	 * Stroke color for the arrow outline.
	 */
	final Color borderColor;
	
	/**
	 * Fill color for the arrow interior.
	 */
	final Color fillColor;
	
	/**
	 * Stroke used when outlining the polygon.
	 */
	final Stroke stroke;

	/**
	 * Creates an arrow overlay description.
	 *
	 * @param move             encoded move
	 * @param arrowHeadHeight  head height in pixels
	 * @param startShortener   pixels trimmed from the start of the segment
	 * @param endShortener     pixels trimmed from the end of the segment
	 * @param borderColor      outline color
	 * @param fillColor        interior fill color
	 * @param stroke           outline stroke
	 */
	public Arrow(short move, int arrowHeadHeight, int startShortener, int endShortener, Color borderColor,
			Color fillColor, Stroke stroke) {
		this.move = move;
		this.arrowHeadHeight = arrowHeadHeight;
		this.startShortener = startShortener;
		this.endShortener = endShortener;
		this.borderColor = borderColor;
		this.fillColor = fillColor;
		this.stroke = stroke;
	}

	/**
	 * Builds the polygon representing this arrow for the given board layout.
	 *
	 * @param boardX        x origin of the board in pixels
	 * @param boardY        y origin of the board in pixels
	 * @param tileWidth     tile width in pixels
	 * @param tileHeight    tile height in pixels
	 * @param whiteSideDown whether the board is oriented with White at the bottom
	 * @return polygon describing the arrow shape
	 */
	Polygon polygon(int boardX, int boardY, int tileWidth, int tileHeight, boolean whiteSideDown) {
		int fromX = whiteSideDown ? Move.getFromX(move) : Move.getFromXInverted(move);
		int fromY = whiteSideDown ? Move.getFromYInverted(move) : Move.getFromY(move);
		int toX = whiteSideDown ? Move.getToX(move) : Move.getToXInverted(move);
		int toY = whiteSideDown ? Move.getToYInverted(move) : Move.getToY(move);

		int startX = boardX + fromX * tileWidth + tileWidth / 2;
		int startY = boardY + fromY * tileHeight + tileHeight / 2;
		int endX = boardX + toX * tileWidth + tileWidth / 2;
		int endY = boardY + toY * tileHeight + tileHeight / 2;

		return buildPolygon(startX, startY, endX, endY, startShortener, endShortener, arrowHeadHeight);
	}

	/**
	 * Builds a polygon for an arrow between two points.
	 *
	 * @param startX          start x in pixels
	 * @param startY          start y in pixels
	 * @param endX            end x in pixels
	 * @param endY            end y in pixels
	 * @param startShortener  pixels trimmed from start
	 * @param endShortener    pixels trimmed from end
	 * @param arrowHeadHeight head height in pixels
	 * @return polygon representing the arrow
	 */
	private static Polygon buildPolygon(int startX, int startY, int endX, int endY, int startShortener,
			int endShortener, int arrowHeadHeight) {
		double dx = (double) endX - startX;
		double dy = (double) endY - startY;
		double length = Math.hypot(dx, dy);
		if (length == 0) {
			return new Polygon(new int[] { startX }, new int[] { startY }, 1);
		}

		double ux = dx / length;
		double uy = dy / length;
		double px = -uy;
		double py = ux;

		double tipX = startX + ux * (length - endShortener);
		double tipY = startY + uy * (length - endShortener);

		double usable = Math.max(0, length - startShortener - endShortener);
		double headHeight = Math.min(arrowHeadHeight, usable);
		if (headHeight <= 0) {
			return new Polygon(new int[] { (int) tipX }, new int[] { (int) tipY }, 1);
		}

		double baseCenterX = tipX - ux * headHeight;
		double baseCenterY = tipY - uy * headHeight;

		double shaftLength = Math.max(0, usable - headHeight);
		double shaftEndX = tipX - ux * headHeight;
		double shaftEndY = tipY - uy * headHeight;
		double shaftStartAlignedX = tipX - ux * (headHeight + shaftLength);
		double shaftStartAlignedY = tipY - uy * (headHeight + shaftLength);

		double halfBaseWidth = headHeight / Math.sqrt(3.0); // equilateral: h = sqrt(3)/2 * side
		// Keep shaft slimmer than the head for clearer silhouette.
		double shaftHalfWidth = Math.max(1.0, Math.min(halfBaseWidth * 0.35, halfBaseWidth - 1.0));

		double baseLeftX = baseCenterX + px * halfBaseWidth;
		double baseLeftY = baseCenterY + py * halfBaseWidth;
		double baseRightX = baseCenterX - px * halfBaseWidth;
		double baseRightY = baseCenterY - py * halfBaseWidth;

		double shaftLeftEndX = shaftEndX + px * shaftHalfWidth;
		double shaftLeftEndY = shaftEndY + py * shaftHalfWidth;
		double shaftRightEndX = shaftEndX - px * shaftHalfWidth;
		double shaftRightEndY = shaftEndY - py * shaftHalfWidth;

		double shaftLeftStartX = shaftStartAlignedX + px * shaftHalfWidth;
		double shaftLeftStartY = shaftStartAlignedY + py * shaftHalfWidth;
		double shaftRightStartX = shaftStartAlignedX - px * shaftHalfWidth;
		double shaftRightStartY = shaftStartAlignedY - py * shaftHalfWidth;

		return new Polygon(
				new int[] {
						(int) Math.round(tipX),
						(int) Math.round(baseLeftX),
						(int) Math.round(shaftLeftEndX),
						(int) Math.round(shaftLeftStartX),
						(int) Math.round(shaftRightStartX),
						(int) Math.round(shaftRightEndX),
						(int) Math.round(baseRightX)
				},
				new int[] {
						(int) Math.round(tipY),
						(int) Math.round(baseLeftY),
						(int) Math.round(shaftLeftEndY),
						(int) Math.round(shaftLeftStartY),
						(int) Math.round(shaftRightStartY),
						(int) Math.round(shaftRightEndY),
						(int) Math.round(baseRightY)
				},
				7);
	}
}
