package utility;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Minimal image helpers for adding a blurred drop shadow to a {@link BufferedImage}.
 *
 * @since 2024
 * @author Lennart A. Conrad
 */
public final class Images {

	/**
	 * Hidden constructor to prevent instantiation of this utility class.
	 */
	private Images() {
		// utility holder
	}

	/**
	 * Adds a blurred drop shadow around an image.
	 *
	 * @param bufferedimage source image
	 * @param blur          blur kernel size (pixels)
	 * @param color         shadow color (alpha ignored)
	 * @return image with shadow padding
	 */
	public static BufferedImage addDropShadow(BufferedImage bufferedimage, int blur, Color color) {
		BufferedImage dropshadow = buildDropShadow(bufferedimage, blur, color);
		Graphics2D graphics2d = dropshadow.createGraphics();
		graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics2d.drawImage(bufferedimage, blur, blur, null);
		graphics2d.dispose();
		return dropshadow;
	}

	/**
	 * Adds a blurred drop shadow around an image with custom offsets.
	 *
	 * @param bufferedimage source image
	 * @param blur          blur kernel size (pixels)
	 * @param xOffset       x offset for the shadow
	 * @param yOffset       y offset for the shadow
	 * @param color         shadow color (alpha ignored)
	 * @return image with shadow padding
	 */
	public static BufferedImage addDropShadow(BufferedImage bufferedimage, int blur, int xOffset, int yOffset,
			Color color) {
		xOffset = Math.max(blur, xOffset);
		yOffset = Math.max(blur, yOffset);
		BufferedImage dropshadow = buildDropShadow(bufferedimage, blur, color);
		BufferedImage result = new BufferedImage(
				bufferedimage.getWidth() + xOffset + xOffset,
				bufferedimage.getHeight() + yOffset + yOffset,
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics2d = result.createGraphics();
		graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics2d.drawImage(dropshadow, blur + xOffset, blur + yOffset,
				bufferedimage.getWidth(), bufferedimage.getHeight(), null);
		graphics2d.drawImage(bufferedimage, blur, blur, null);
		graphics2d.dispose();
		return result;
	}

	/**
	 * Builds a shadow-only image by blurring the source alpha channel.
	 *
	 * @param bufferedimage source image whose alpha defines the shadow shape
	 * @param shadowblur    blur radius in pixels
	 * @param color         shadow color (alpha ignored)
	 * @return blurred shadow image sized to include the blur padding
	 */
	private static BufferedImage buildDropShadow(BufferedImage bufferedimage, int shadowblur, Color color) {
		ShadowSpec spec = ShadowSpec.of(bufferedimage, shadowblur, color);
		int[] srcBuffer = new int[spec.srcWidth * spec.srcHeight];
		int[] dstBuffer = new int[spec.dstWidth * spec.dstHeight];
		int[] aHistory = new int[spec.shadowSize];
		getPixels(bufferedimage, 0, 0, spec.srcWidth, spec.srcHeight, srcBuffer);

		int[] hSumLookup = buildLookup(spec.shadowSize, 1.0f / spec.shadowSize);
		int[] vSumLookup = buildLookup(spec.shadowSize, 1.0f / spec.shadowSize);

		horizontalBlur(spec, srcBuffer, dstBuffer, aHistory, hSumLookup);
		verticalBlur(spec, dstBuffer, aHistory, vSumLookup);

		BufferedImage dst = new BufferedImage(spec.dstWidth, spec.dstHeight, BufferedImage.TYPE_INT_ARGB);
		setPixels(dst, 0, 0, spec.dstWidth, spec.dstHeight, dstBuffer);
		return dst;
	}

	/**
	 * Builds a lookup table that converts alpha sums into averaged alpha values.
	 *
	 * @param shadowSize kernel size in pixels
	 * @param divider    scale factor used for averaging
	 * @return lookup table indexed by alpha sums
	 */
	private static int[] buildLookup(int shadowSize, float divider) {
		int[] lookup = new int[256 * shadowSize];
		for (int i = 0; i < lookup.length; i++) {
			lookup[i] = (int) (i * divider);
		}
		return lookup;
	}

	/**
	 * Applies the horizontal box blur pass to the alpha channel.
	 *
	 * @param spec       precomputed shadow dimensions
	 * @param srcBuffer  source ARGB buffer
	 * @param dstBuffer  destination buffer storing blurred alpha
	 * @param aHistory   rolling alpha history buffer
	 * @param hSumLookup lookup table for horizontal sums
	 */
	private static void horizontalBlur(ShadowSpec spec, int[] srcBuffer, int[] dstBuffer, int[] aHistory,
			int[] hSumLookup) {
		for (int srcY = 0; srcY < spec.srcHeight; srcY++) {
			int dstOffset = spec.left * spec.dstWidth + srcY * spec.dstWidth;
			resetHistory(aHistory, spec.shadowSize);

			int aSum = 0;
			int historyIdx = 0;
			int srcOffset = srcY * spec.srcWidth;

			for (int srcX = 0; srcX < spec.srcWidth; srcX++) {
				int a = hSumLookup[aSum];
				dstBuffer[dstOffset + srcX] = a << 24;

				aSum -= aHistory[historyIdx];

				a = srcBuffer[srcOffset + srcX] >>> 24;
				aHistory[historyIdx] = a;
				aSum += a;

				if (++historyIdx >= spec.shadowSize) {
					historyIdx -= spec.shadowSize;
				}
			}

			int tailOffset = dstOffset + spec.srcWidth;
			for (int i = 0; i < spec.shadowSize; i++) {
				int a = hSumLookup[aSum];
				dstBuffer[tailOffset + i] = a << 24;

				aSum -= aHistory[historyIdx];

				if (++historyIdx >= spec.shadowSize) {
					historyIdx -= spec.shadowSize;
				}
			}
		}
	}

	/**
	 * Applies the vertical box blur pass and writes the final shadow color.
	 *
	 * @param spec       precomputed shadow dimensions
	 * @param dstBuffer  buffer produced by the horizontal pass and updated in-place
	 * @param aHistory   rolling alpha history buffer
	 * @param vSumLookup lookup table for vertical sums
	 */
	private static void verticalBlur(ShadowSpec spec, int[] dstBuffer, int[] aHistory, int[] vSumLookup) {
		for (int x = 0; x < spec.dstWidth; x++) {
			int aSum = 0;

			resetHistory(aHistory, spec.left);

			int historyIdx = spec.left;
			for (int y = 0; y < spec.right; y++) {
				int bufferOffset = x + y * spec.dstWidth;
				int a = dstBuffer[bufferOffset] >>> 24;
				aHistory[historyIdx++] = a;
				aSum += a;
			}

			historyIdx = 0;

			for (int y = 0; y < spec.yStop; y++) {
				int bufferOffset = x + y * spec.dstWidth;
				int a = vSumLookup[aSum];
				dstBuffer[bufferOffset] = a << 24 | spec.shadowRgb;

				aSum -= aHistory[historyIdx];

				a = dstBuffer[bufferOffset + spec.lastPixelOffset] >>> 24;
				aHistory[historyIdx] = a;
				aSum += a;

				if (++historyIdx >= spec.shadowSize) {
					historyIdx -= spec.shadowSize;
				}
			}

			for (int y = spec.yStop; y < spec.dstHeight; y++) {
				int bufferOffset = x + y * spec.dstWidth;
				int a = vSumLookup[aSum];
				dstBuffer[bufferOffset] = a << 24 | spec.shadowRgb;

				aSum -= aHistory[historyIdx];

				if (++historyIdx >= spec.shadowSize) {
					historyIdx -= spec.shadowSize;
				}
			}
		}
	}

	/**
	 * Clears the rolling alpha history buffer for the requested number of entries.
	 *
	 * @param aHistory history buffer to clear
	 * @param count    number of entries to reset
	 */
	private static void resetHistory(int[] aHistory, int count) {
		for (int i = 0; i < count; i++) {
			aHistory[i] = 0;
		}
	}

	/**
	 * Derived dimensions and offsets used during shadow blur.
	 */
	private static final class ShadowSpec {

		/**
		 * Width of the source image.
		 */
		private final int srcWidth;

		/**
		 * Height of the source image.
		 */
		private final int srcHeight;

		/**
		 * Width of the destination shadow image.
		 */
		private final int dstWidth;

		/**
		 * Height of the destination shadow image.
		 */
		private final int dstHeight;

		/**
		 * Size of the blur kernel in pixels.
		 */
		private final int shadowSize;

		/**
		 * Left padding applied when expanding the destination image.
		 */
		private final int left;

		/**
		 * Right padding applied when expanding the destination image.
		 */
		private final int right;

		/**
		 * Vertical loop stop position for the blur pass.
		 */
		private final int yStop;

		/**
		 * Offset used when sampling the last pixel column for the blur pass.
		 */
		private final int lastPixelOffset;

		/**
		 * RGB color used for the shadow (alpha excluded).
		 */
		private final int shadowRgb;

		/**
		 * Builds a shadow specification from a source image and blur size.
		 *
		 * @param image      source image
		 * @param shadowblur blur radius in pixels
		 * @param color      shadow color (alpha ignored)
		 */
		private ShadowSpec(BufferedImage image, int shadowblur, Color color) {
			this.shadowSize = shadowblur * 2;
			this.srcWidth = image.getWidth();
			this.srcHeight = image.getHeight();
			this.dstWidth = srcWidth + shadowSize;
			this.dstHeight = srcHeight + shadowSize;
			this.left = shadowblur;
			this.right = shadowSize - left;
			this.yStop = dstHeight - right;
			this.lastPixelOffset = right * dstWidth;
			this.shadowRgb = color.getRGB() & 0x00FFFFFF;
		}

		/**
		 * Factory for creating a {@link ShadowSpec}.
		 *
		 * @param image      source image
		 * @param shadowblur blur radius in pixels
		 * @param color      shadow color (alpha ignored)
		 * @return shadow specification
		 */
		private static ShadowSpec of(BufferedImage image, int shadowblur, Color color) {
			return new ShadowSpec(image, shadowblur, color);
		}
	}

	/**
	 * Reads pixels from a {@link BufferedImage} into a packed ARGB buffer.
	 *
	 * @param img    source image
	 * @param x      x origin for the read
	 * @param y      y origin for the read
	 * @param w      width of the region
	 * @param h      height of the region
	 * @param pixels optional preallocated buffer
	 * @return buffer containing ARGB pixels
	 */
	private static int[] getPixels(BufferedImage img, int x, int y, int w, int h, int[] pixels) {
		if (w == 0 || h == 0) {
			return new int[0];
		}
		if (pixels == null) {
			pixels = new int[w * h];
		} else if (pixels.length < w * h) {
			throw new IllegalArgumentException("pixels array must have a length >= w*h");
		}

		int imageType = img.getType();
		if (imageType == BufferedImage.TYPE_INT_ARGB || imageType == BufferedImage.TYPE_INT_RGB) {
			Raster raster = img.getRaster();
			return (int[]) raster.getDataElements(x, y, w, h, pixels);
		}
		return img.getRGB(x, y, w, h, pixels, 0, w);
	}

	/**
	 * Writes packed ARGB pixels into a {@link BufferedImage}.
	 *
	 * @param img    destination image
	 * @param x      x origin for the write
	 * @param y      y origin for the write
	 * @param w      width of the region
	 * @param h      height of the region
	 * @param pixels buffer containing ARGB pixels
	 */
	private static void setPixels(BufferedImage img, int x, int y, int w, int h, int[] pixels) {
		if (pixels == null || w == 0 || h == 0) {
			return;
		} else if (pixels.length < w * h) {
			throw new IllegalArgumentException("pixels array must have a length >= w*h");
		}

		int imageType = img.getType();
		if (imageType == BufferedImage.TYPE_INT_ARGB || imageType == BufferedImage.TYPE_INT_RGB) {
			WritableRaster raster = img.getRaster();
			raster.setDataElements(x, y, w, h, pixels);
		} else {
			img.setRGB(x, y, w, h, pixels, 0, w);
		}
	}

}
