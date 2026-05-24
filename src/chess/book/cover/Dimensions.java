package chess.book.cover;

import chess.book.model.Book;
import chess.pdf.document.PageSize;

/**
 * Physical cover dimensions in centimeters.
 *
 * @param binding binding layout
 * @param interior interior paper choice
 * @param pages printed page count used for the spine
 * @param trimWidthCm interior trim width
 * @param trimHeightCm interior trim height
 * @param spineWidthCm physical spine width
 * @param fullWidthCm full cover sheet width
 * @param fullHeightCm full cover sheet height
 * @param back back-cover safe text area
 * @param spine spine safe text area
 * @param front front-cover safe text area
 * @since 2026
 * @author Lennart A. Conrad
 */
public record Dimensions(
	/**
	 * Stores the binding.
	 */
	Binding binding,
	/**
	 * Stores the interior.
	 */
	Interior interior,
	/**
	 * Stores the pages.
	 */
	int pages,
	/**
	 * Stores the trim width cm.
	 */
	double trimWidthCm,
	/**
	 * Stores the trim height cm.
	 */
	double trimHeightCm,
	/**
	 * Stores the spine width cm.
	 */
	double spineWidthCm,
	/**
	 * Stores the full width cm.
	 */
	double fullWidthCm,
	/**
	 * Stores the full height cm.
	 */
	double fullHeightCm,
	/**
	 * Stores the back.
	 */
	Area back,
	/**
	 * Stores the spine.
	 */
	Area spine,
	/**
	 * Stores the front.
	 */
	Area front
) {

	/**
	 * Creates one validated dimension set.
	 *
	 * @param binding binding layout
	 * @param interior interior paper choice
	 * @param pages printed page count used for the spine
	 * @param trimWidthCm interior trim width
	 * @param trimHeightCm interior trim height
	 * @param spineWidthCm physical spine width
	 * @param fullWidthCm full cover sheet width
	 * @param fullHeightCm full cover sheet height
	 * @param back back-cover safe text area
	 * @param spine spine safe text area
	 * @param front front-cover safe text area
	 */
	public Dimensions {
		if (binding == null) {
			throw new IllegalArgumentException("binding cannot be null");
		}
		if (interior == null) {
			throw new IllegalArgumentException("interior cannot be null");
		}
		if (pages < 0) {
			throw new IllegalArgumentException("pages cannot be negative");
		}
		if (trimWidthCm <= 0.0 || trimHeightCm <= 0.0 || fullWidthCm <= 0.0 || fullHeightCm <= 0.0) {
			throw new IllegalArgumentException("cover dimensions must be positive");
		}
		back = back == null ? Area.EMPTY : back;
		spine = spine == null ? Area.EMPTY : spine;
		front = front == null ? Area.EMPTY : front;
	}

	/**
	 * Returns whether this cover has a printable spine.
	 *
	 * @return true when the spine area is non-empty
	 */
	public boolean hasSpine() {
		return spine.widthCm() > 0.0 && spine.heightCm() > 0.0;
	}

	/**
	 * Converts the cover dimensions to a PDF page size.
	 *
	 * @return page size matching the full cover sheet
	 */
	public PageSize toPageSize() {
		return new PageSize("Cover", Book.cmToPoints(fullWidthCm), Book.cmToPoints(fullHeightCm));
	}

	/**
	 * Rectangular cover area measured in centimeters.
	 *
	 * @param xCm left edge
	 * @param yCm top edge
	 * @param widthCm width
	 * @param heightCm height
	 */
	public record Area(
		/**
		 * Stores the x cm.
		 */
		double xCm,
		/**
		 * Stores the y cm.
		 */
		double yCm,
		/**
		 * Stores the width cm.
		 */
		double widthCm,
		/**
		 * Stores the height cm.
		 */
		double heightCm
	) {

		/**
		 * Shared empty area.
		 */
		private static final Area EMPTY = new Area(0.0, 0.0, 0.0, 0.0);

		/**
		 * Creates one validated area.
		 *
		 * @param xCm left edge
		 * @param yCm top edge
		 * @param widthCm width
		 * @param heightCm height
		 */
		public Area {
			if (xCm < 0.0 || yCm < 0.0 || widthCm < 0.0 || heightCm < 0.0) {
				throw new IllegalArgumentException("area coordinates cannot be negative");
			}
		}

		/**
		 * Returns the right edge.
		 *
		 * @return right edge in centimeters
		 */
		public double rightCm() {
			return xCm + widthCm;
		}

		/**
		 * Returns the bottom edge.
		 *
		 * @return bottom edge in centimeters
		 */
		public double bottomCm() {
			return yCm + heightCm;
		}
	}
}
