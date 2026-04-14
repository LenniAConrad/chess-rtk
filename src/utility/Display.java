package utility;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility class for displaying a {@code BufferedImage} in a {@code JFrame}
 * whilst keeping its aspect ratio.
 * 
 * @implSpec Use {@code System.setProperty("sun.java2d.uiScale", "1.0");} as the
 *           very first command in your {@code main()} method if you want to
 *           remove uiScaling by the operating system
 * 
 * @since 2024
 * @author Lennart A. Conrad
 */
public class Display extends JFrame {

	/**
	 * Default serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Default title used for display windows.
	 */
	private static final String DEFAULT_WINDOW_TITLE = "Chess-RTK Board View";

	/**
	 * Panel responsible for rendering the buffered image.
	 */
	private DisplayPanel imageDisplay = null;

	/**
	 * Render source that can redraw itself at arbitrary display sizes.
	 */
	public interface ImageSource {

		/**
		 * Native source width.
		 *
		 * @return native width in pixels
		 */
		int width();

		/**
		 * Native source height.
		 *
		 * @return native height in pixels
		 */
		int height();

		/**
		 * Renders the source at the requested full output size.
		 *
		 * @param width requested output width
		 * @param height requested output height
		 * @return rendered image
		 */
		BufferedImage render(int width, int height);

		/**
		 * Whether cache misses during interaction should render the visible region immediately.
		 *
		 * @return {@code true} for vector/redrawable sources that can cheaply render a viewport
		 */
		default boolean renderRegionImmediately() {
			return false;
		}

		/**
		 * Whether this source can export a real SVG document.
		 *
		 * @return {@code true} when SVG export is available
		 */
		default boolean supportsSvgExport() {
			return false;
		}

		/**
		 * Renders this source as an SVG document.
		 *
		 * @param width requested SVG width
		 * @param height requested SVG height
		 * @return SVG source text
		 */
		default String renderSvg(int width, int height) {
			throw new UnsupportedOperationException("SVG export is not supported by this image source");
		}

		/**
		 * Renders a visible region from a scaled output image.
		 *
		 * @param scaledWidth full scaled output width
		 * @param scaledHeight full scaled output height
		 * @param sourceX x coordinate of the visible region within the scaled output
		 * @param sourceY y coordinate of the visible region within the scaled output
		 * @param width visible region width
		 * @param height visible region height
		 * @return rendered visible region
		 */
		default BufferedImage renderRegion(int scaledWidth, int scaledHeight, int sourceX, int sourceY,
				int width, int height) {
			BufferedImage full = render(scaledWidth, scaledHeight);
			BufferedImage region = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = region.createGraphics();
			g2d.drawImage(full,
					0,
					0,
					width,
					height,
					sourceX,
					sourceY,
					sourceX + width,
					sourceY + height,
					null);
			g2d.dispose();
			return region;
		}
	}
	
	/**
	 * Constructor method for {@code Display}.
	 *
	 * <p>Creates a visible frame with the given dimensions and theme, and
	 * initializes the panel state from the supplied image.</p>
	 *
	 * @param image  the {@code BufferedImage} that is to be displayed
	 * @param width  the width of the {@code JFrame} upon execution
	 * @param height the height of the {@code JFrame} upon execution
	 * @param light  whether the window will be displayed in light mode
	 * @throws NullPointerException if {@code image} is {@code null}
	 */
	public Display(BufferedImage image, int width, int height, boolean light) {
		this(imageSource(image), width, height, light);
	}

	/**
	 * Constructor method for vector/redrawable sources.
	 *
	 * @param source redrawable image source
	 * @param width the width of the {@code JFrame} upon execution
	 * @param height the height of the {@code JFrame} upon execution
	 * @param light whether the window will be displayed in light mode
	 */
	public Display(ImageSource source, int width, int height, boolean light) {
		ImageSource validated = requireImageSource(source);
		BufferedImage iconSource = validated.render(validated.width(), validated.height());
		imageDisplay = new DisplayPanel(validated, this, light);
		this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		this.setSize(width, height);
		this.add(imageDisplay);
		this.setVisible(true);
		this.setIconImages(buildIconVariants(iconSource));
	}
	
	/**
	 * Constructor method for {@code Display}.
	 *
	 * <p>Creates a visible frame with the provided size using light mode.</p>
	 *
	 * @param image  the {@code BufferedImage} that is to be displayed
	 * @param width  the width of the {@code JFrame} upon execution
	 * @param height the height of the {@code JFrame} upon execution
	 * @throws NullPointerException if {@code image} is {@code null}
	 */
	public Display(BufferedImage image, int width, int height) {
		this(imageSource(image), width, height, true);
	}

	/**
	 * Constructor method for vector/redrawable sources.
	 *
	 * @param source redrawable image source
	 * @param width the width of the {@code JFrame} upon execution
	 * @param height the height of the {@code JFrame} upon execution
	 */
	public Display(ImageSource source, int width, int height) {
		this(source, width, height, true);
	}
	
	/**
	 * Used for displaying an image in a 400x400 {@code JFrame} with optional light/dark theme.
	 *
	 * <p>The frame is shown immediately using the requested theme.</p>
	 *
	 * @param image the {@code BufferedImage} to display
	 * @param light whether to display the frame in light mode
	 * @throws NullPointerException if {@code image} is {@code null}
	 */
	public Display(BufferedImage image, boolean light) {
		this(imageSource(image), 400, 400, light);
	}

	/**
	 * Used for displaying a redrawable source in a 400x400 {@code JFrame}.
	 *
	 * @param source redrawable image source
	 * @param light whether to display the frame in light mode
	 */
	public Display(ImageSource source, boolean light) {
		this(source, 400, 400, light);
	}
	
	/**
	 * Constructor method for {@code Display}.
	 *
	 * <p>Creates a 400x400 light-mode frame for the provided image.</p>
	 *
	 * @param image the {@code BufferedImage} that is to be displayed
	 * @throws NullPointerException if {@code image} is {@code null}
	 */
	public Display(BufferedImage image) {
		this(imageSource(image), 400, 400, true);
	}

	/**
	 * Constructor method for a 400x400 light-mode frame backed by a redrawable source.
	 *
	 * @param source redrawable image source
	 */
	public Display(ImageSource source) {
		this(source, 400, 400, true);
	}

	/**
	 * Used for updating the displayed image after construction.
	 *
	 * <p>Resets cached scaling state and triggers a repaint of the panel.</p>
	 *
	 * @param image the new {@code BufferedImage} to display
	 * @return this {@code Display} instance for method chaining
	 * @throws NullPointerException if {@code image} is {@code null}
	 */
	public Display setImage(BufferedImage image) {
		imageDisplay.setImageSource(imageSource(image));
		repaint();
		return this;
	}

	/**
	 * Used for updating the displayed redrawable source after construction.
	 *
	 * @param source the new source to display
	 * @return this {@code Display} instance for method chaining
	 */
	public Display setImageSource(ImageSource source) {
		imageDisplay.setImageSource(requireImageSource(source));
		repaint();
		return this;
	}

	/**
	 * Used for updating the zoom factor after construction.
	 *
	 * <p>The zoom value is clamped to the supported range defined by the panel.</p>
	 *
	 * @param zoom the zoom factor applied on top of the fit-to-window scale
	 * @return this {@code Display} instance for method chaining
	 */
	public Display setZoom(double zoom) {
		imageDisplay.setZoom(zoom);
		repaint();
		return this;
	}

	/**
	 * Used for setting the base window title.
	 *
	 * @param title base title text
	 * @return this {@code Display} instance for method chaining
	 */
	public Display setDisplayTitle(String title) {
		imageDisplay.setTitlePrefix(title);
		return this;
	}

	/**
	 * Validate that the provided image is non-null, mirroring the null checks used across the constructors.
	 *
	 * @param image image to validate
	 * @return the same image when non-null
	 * @throws NullPointerException if {@code image} is {@code null}
	 */
	private static BufferedImage requireImage(BufferedImage image) {
		return Objects.requireNonNull(image, "image must not be null");
	}

	/**
	 * Wraps a buffered image in a redrawable source.
	 *
	 * @param image image to wrap
	 * @return image source
	 */
	private static ImageSource imageSource(BufferedImage image) {
		return new BufferedImageSource(requireImage(image));
	}

	/**
	 * Validates an image source.
	 *
	 * @param source source to validate
	 * @return the same source when valid
	 */
	private static ImageSource requireImageSource(ImageSource source) {
		ImageSource validated = Objects.requireNonNull(source, "image source must not be null");
		if (validated.width() <= 0 || validated.height() <= 0) {
			throw new IllegalArgumentException("image source dimensions must be positive");
		}
		return validated;
	}

	/**
	 * Redrawable wrapper for ordinary raster images.
	 */
	private static final class BufferedImageSource implements ImageSource {

		/**
		 * Backing image.
		 */
		private final BufferedImage image;

		/**
		 * Creates a wrapper around a buffered image.
		 *
		 * @param image backing image
		 */
		private BufferedImageSource(BufferedImage image) {
			this.image = image;
		}

		@Override
		public int width() {
			return image.getWidth();
		}

		@Override
		public int height() {
			return image.getHeight();
		}

		@Override
		public BufferedImage render(int width, int height) {
			int w = Math.max(1, width);
			int h = Math.max(1, height);
			if (w == image.getWidth() && h == image.getHeight()) {
				return image;
			}
			return scaleImage(image, w, h);
		}

		@Override
		public BufferedImage renderRegion(int scaledWidth, int scaledHeight, int sourceX, int sourceY,
				int width, int height) {
			BufferedImage region = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = region.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Rectangle src = scaledToNativeSourceRect(
					image.getWidth(),
					image.getHeight(),
					scaledWidth,
					scaledHeight,
					sourceX,
					sourceY,
					width,
					height);
			g2d.drawImage(image,
					0,
					0,
					width,
					height,
					src.x,
					src.y,
					src.x + src.width,
					src.y + src.height,
					null);
			g2d.dispose();
			return region;
		}
	}

	/**
	 * Converts a visible rectangle in scaled-image coordinates to the corresponding native source rectangle.
	 *
	 * @param nativeWidth source image native width
	 * @param nativeHeight source image native height
	 * @param scaledWidth full scaled image width
	 * @param scaledHeight full scaled image height
	 * @param sourceX visible x coordinate inside the scaled image
	 * @param sourceY visible y coordinate inside the scaled image
	 * @param width visible width in scaled-image coordinates
	 * @param height visible height in scaled-image coordinates
	 * @return native source rectangle
	 */
	private static Rectangle scaledToNativeSourceRect(int nativeWidth, int nativeHeight, int scaledWidth,
			int scaledHeight, int sourceX, int sourceY, int width, int height) {
		if (nativeWidth <= 0 || nativeHeight <= 0 || scaledWidth <= 0 || scaledHeight <= 0) {
			return new Rectangle(0, 0, Math.max(1, nativeWidth), Math.max(1, nativeHeight));
		}

		double scaleX = nativeWidth / (double) scaledWidth;
		double scaleY = nativeHeight / (double) scaledHeight;
		int x1 = clampIntStatic((int) Math.floor(sourceX * scaleX), 0, nativeWidth);
		int y1 = clampIntStatic((int) Math.floor(sourceY * scaleY), 0, nativeHeight);
		int x2 = clampIntStatic((int) Math.ceil((sourceX + width) * scaleX), 0, nativeWidth);
		int y2 = clampIntStatic((int) Math.ceil((sourceY + height) * scaleY), 0, nativeHeight);
		if (x2 <= x1) {
			x2 = Math.min(nativeWidth, x1 + 1);
			x1 = Math.max(0, x2 - 1);
		}
		if (y2 <= y1) {
			y2 = Math.min(nativeHeight, y1 + 1);
			y1 = Math.max(0, y2 - 1);
		}
		return new Rectangle(x1, y1, x2 - x1, y2 - y1);
	}

	/**
	 * Clamps an integer without requiring a panel instance.
	 *
	 * @param value value to clamp
	 * @param min minimum inclusive
	 * @param max maximum inclusive
	 * @return clamped value
	 */
	private static int clampIntStatic(int value, int min, int max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
	
	/**
	 * Build multiple high-quality scaled variants of the icon to avoid blurry scaling in the system tray.
	 *
	 * <p>The returned list includes several common sizes plus the original image.</p>
	 *
	 * @param image the source icon
	 * @return list of scaled icons from small tray size up to the original dimensions
	 */
	private static List<Image> buildIconVariants(BufferedImage image) {
		List<Image> icons = new ArrayList<>();
		if (image == null) {
			return icons;
		}
		int[] targetSizes = {16, 24, 32, 48, 64, 128};
		for (int size : targetSizes) {
			icons.add(scaleImage(image, size, size));
		}
		icons.add(image);
		return icons;
	}

	/**
	 * Scale an image with high quality interpolation.
	 *
	 * <p>Uses bicubic interpolation to preserve detail when resizing.</p>
	 *
	 * @param source the image to scale
	 * @param width  target width
	 * @param height target height
	 * @return a new {@link BufferedImage} of the desired size
	 * @throws NullPointerException if {@code source} is {@code null}
	 */
	private static BufferedImage scaleImage(BufferedImage source, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = scaled.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.drawImage(source, 0, 0, width, height, null);
		g2d.dispose();
		return scaled;
	}

	/**
	 * New {@code JPanel} class that draws the centered {@code BufferedImage}.
	 *
	 * <p>This panel also manages zoom, pan, checkerboard backgrounds for
	 * transparency, and cached scaling to keep rendering responsive.</p>
	 */
	private class DisplayPanel extends JPanel {

		/**
		 * Used for defining light mode background color.
		 */
		private static final Color LIGHT_BACKGROUND = new Color(255, 255, 255);
		
		/**
		 * Used for defining light mode border color.
		 */
		private static final Color LIGHT_BORDER = new Color(192, 192, 192);
		
		/**
		 * Used for defining light mode transparent tile color 1.
		 */
		private static final Color LIGHT_TRANSPARENT_1 = new Color(230, 230, 230);
		
		/**
		 * Used for defining light mode transparent tile color 2.
		 */
		private static final Color LIGHT_TRANSPARENT_2 = new Color(245, 245, 245);
		
		/**
		 * Used for defining dark mode background color.
		 */
		private static final Color DARK_BACKGROUND = new Color(25, 25, 25);
		
		/**
		 * Used for defining dark mode border color.
		 */
		private static final Color DARK_BORDER = new Color(75, 75, 75);
		
		/**
		 * Used for defining dark mode transparent tile color 1.
		 */
		private static final Color DARK_TRANSPARENT_1 = new Color(100, 100, 100);
		
		/**
		 * Used for defining dark mode transparent tile color 2.
		 */
		private static final Color DARK_TRANSPARENT_2 = new Color(150, 150, 150);
		
		/**
		 * Pixel size for the transparent background tiles
		 */
		private static final int TILESIZE = 5;

		/**
		 * Action key identifier for saving the current image.
		 */
		private static final String SAVE_ACTION_KEY = "display.saveImage";

		/**
		 * Action key identifier for copying the current image.
		 */
		private static final String COPY_ACTION_KEY = "display.copyImage";

		/**
		 * Dialog title used for save image prompts.
		 */
		private static final String SAVE_DIALOG_TITLE = "Save Image";

		/**
		 * HUD hint shown in the display window.
		 */
		private static final String HELP_TEXT = "Ctrl/Cmd+S to save, Ctrl/Cmd+C to copy, mouse wheel to zoom";

		/**
		 * Padding around the HUD hint box.
		 */
		private static final int HELP_PADDING = 8;

		/**
		 * Alpha channel for the HUD background.
		 */
		private static final int HELP_BG_ALPHA = 200;

		/**
		 * Pixel-perfect border thickness around the displayed image.
		 */
		private static final int IMAGE_BORDER_THICKNESS = 1;

		/**
		 * Minimum zoom factor applied on top of fit-to-window scaling.
		 */
		private static final double MIN_ZOOM = 0.05;

		/**
		 * Maximum zoom factor applied on top of fit-to-window scaling.
		 */
		private static final double MAX_ZOOM = 64.0;

		/**
		 * Per-notch zoom multiplier for mouse wheel input.
		 */
		private static final double ZOOM_STEP = 1.1;
		
		/**
		 * Base title shown in the window manager.
		 */
		private String titlePrefix = DEFAULT_WINDOW_TITLE;

		/**
		 * Timestamp shown in the window title.
		 */
		private String titleTimestamp = Dates.getTimestamp();

		/**
		 * Default serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * Redrawable source backing this display.
		 */
		private transient ImageSource imageSource;

		/**
		 * Native preview image used for fallback drawing and export.
		 */
		private transient BufferedImage image;

		/**
		 * The {@code JFrame} that the image is going to be displayed in
		 */
		private JFrame jframe;

		/**
		 * The width of our image that is to be rendered
		 */
		private int imageWidth = 0;

		/**
		 * The height of our image that is to be rendered
		 */
		private int imageHeight = 0;

		/**
		 * Zoom multiplier applied after fitting the image to the panel.
		 */
		private double zoom = 1.0;

		/**
		 * Current pan offset in pixels along the x-axis.
		 */
		private int panX = 0;

		/**
		 * Current pan offset in pixels along the y-axis.
		 */
		private int panY = 0;

		/**
		 * Last mouse position for drag-based panning.
		 */
		private Point lastDragPoint = null;
		
		/**
		 * Cached scaled image to avoid resampling on every repaint.
		 */
		private transient BufferedImage scaledImageCache = null;

		/**
		 * Cached scaled image width.
		 */
		private int scaledWidth = -1;

		/**
		 * Cached scaled image height.
		 */
		private int scaledHeight = -1;

		/**
		 * X coordinate of the cached slice inside the scaled image.
		 */
		private int cachedSourceX = -1;

		/**
		 * Y coordinate of the cached slice inside the scaled image.
		 */
		private int cachedSourceY = -1;

		/**
		 * Width of the cached visible slice.
		 */
		private int cachedSliceWidth = -1;

		/**
		 * Height of the cached visible slice.
		 */
		private int cachedSliceHeight = -1;

		/**
		 * Reusable checkerboard texture for transparent background.
		 */
		private transient TexturePaint checkerPaint;

		/**
		 * Debounces resize events before regenerating a high-quality scaled buffer.
		 */
		private transient Timer resizeDebounceTimer;

		/**
		 * Background worker used to scale the image without blocking the EDT.
		 */
		private transient SwingWorker<BufferedImage, Void> scaleWorker;

		/**
		 * Tracks active user interaction (zoom/pan) to defer heavy scaling work.
		 */
		private boolean interactionActive = false;

		/**
		 * Timer used to detect the end of a burst of interaction events.
		 */
		private transient Timer interactionTimer;

		/**
		 * Monotonic request id so stale workers cannot overwrite newer scale requests.
		 */
		private long scaleRequestId = 0L;

		/**
		 * Pending target dimensions for the next scale request.
		 */
		private int pendingScaledWidth = -1;

		/**
		 * Pending target dimensions for the next scale request.
		 */
		private int pendingScaledHeight = -1;
		
		/**
		 * Used for holding the background color depending on light/dark mode.
		 */
		private Color backgroundColor = null;

		/**
		 * Used for holding the border color depending on light/dark mode.
		 */
		private Color borderColor = null;

		/**
		 * Used for holding the first transparent tile color depending on light/dark mode.
		 */
		private Color transparentColor1 = null;

		/**
		 * Used for holding the second transparent tile color depending on light/dark mode.
		 */
		private Color transparentColor2 = null;
		
		/**
		 * Constructs a display panel for rendering the given image.
		 *
		 * <p>Initializes sizing state, theme colors, and event handlers for zoom/pan.</p>
		 *
		 * @param source redrawable source that is to be displayed
		 * @param jframe owning frame used for title updates
		 * @param light whether to use the light theme palette
		 */
		public DisplayPanel(ImageSource source, JFrame jframe, boolean light) {
			this.imageSource = requireImageSource(source);
			this.image = imageSource.render(imageSource.width(), imageSource.height());
			this.jframe = jframe;
			this.imageWidth = imageSource.width();
			this.imageHeight = imageSource.height();
			if(light) {
				backgroundColor = LIGHT_BACKGROUND;
				borderColor = LIGHT_BORDER;
				transparentColor1 = LIGHT_TRANSPARENT_1;
				transparentColor2 = LIGHT_TRANSPARENT_2;
			} else {
				backgroundColor = DARK_BACKGROUND;
				borderColor = DARK_BORDER;
				transparentColor1 = DARK_TRANSPARENT_1;
				transparentColor2 = DARK_TRANSPARENT_2;
			}
			setBackground(backgroundColor);
			setOpaque(true);
			checkerPaint = buildCheckerPaint();

			resizeDebounceTimer = new Timer(120, e -> triggerScaleToPendingSize());
			resizeDebounceTimer.setRepeats(false);
			interactionTimer = new Timer(160, e -> endInteraction());
			interactionTimer.setRepeats(false);

			addComponentListener(new java.awt.event.ComponentAdapter() {

				/**
				 * Reacts to panel resize events by recomputing scale and pan constraints.
				 *
				 * @param e resize event payload
				 */
				@Override
				public void componentResized(java.awt.event.ComponentEvent e) {
					scheduleScaleToFit();
					clampPanToBounds();
				}
			});
			addMouseWheelListener(this::handleZoomWheel);
			addMouseListener(new java.awt.event.MouseAdapter() {

				/**
				 * Captures the start point for drag-based panning.
				 *
				 * @param e mouse press event
				 */
				@Override
				public void mousePressed(java.awt.event.MouseEvent e) {
					lastDragPoint = e.getPoint();
				}

				/**
				 * Clears the drag reference when panning ends.
				 *
				 * @param e mouse release event
				 */
				@Override
				public void mouseReleased(java.awt.event.MouseEvent e) {
					lastDragPoint = null;
				}
			});
			addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {

				/**
				 * Applies pan deltas as the mouse is dragged.
				 *
				 * @param e mouse drag event
				 */
				@Override
				public void mouseDragged(java.awt.event.MouseEvent e) {
					handlePanDrag(e.getPoint());
				}
			});
			registerSaveShortcut();
			registerCopyShortcut();
			updateFrameTitle();
		}

		/**
		 * Hooks into the Swing component lifecycle once the panel is realized.
		 * Schedules an initial scale pass so the first paint uses a cached size.
		 */
		@Override
		public void addNotify() {
			super.addNotify();
			SwingUtilities.invokeLater(() -> {
				scheduleScaleToFit();
				triggerScaleToPendingSize();
			});
		}

		/**
		 * Builds a small checkerboard pattern used as the texture paint for transparent backgrounds.
		 *
		 * @return a reusable {@link TexturePaint} containing the checkerboard tiles
		 */
		private TexturePaint buildCheckerPaint() {
			BufferedImage pattern = new BufferedImage(TILESIZE * 2, TILESIZE * 2, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = pattern.createGraphics();
			g2d.setColor(transparentColor1);
			g2d.fillRect(0, 0, TILESIZE * 2, TILESIZE * 2);
			g2d.setColor(transparentColor2);
			g2d.fillRect(0, 0, TILESIZE, TILESIZE);
			g2d.fillRect(TILESIZE, TILESIZE, TILESIZE, TILESIZE);
			g2d.dispose();
			return new TexturePaint(pattern, new Rectangle(0, 0, TILESIZE * 2, TILESIZE * 2));
		}

		/**
		 * Updates the image source and resets cached render data.
		 *
		 * @param source the new image source to display
		 */
		private void setImageSource(ImageSource source) {
			this.imageSource = requireImageSource(source);
			this.image = imageSource.render(imageSource.width(), imageSource.height());
			this.imageWidth = imageSource.width();
			this.imageHeight = imageSource.height();
			this.panX = 0;
			this.panY = 0;
			this.lastDragPoint = null;
			// Invalidate cache so the next scale request recalculates.
			this.scaledWidth = -1;
			this.scaledHeight = -1;
			this.cachedSourceX = -1;
			this.cachedSourceY = -1;
			this.cachedSliceWidth = -1;
			this.cachedSliceHeight = -1;
			this.scaledImageCache = null;
			this.interactionActive = false;
			if (interactionTimer != null) {
				interactionTimer.stop();
			}
			cancelScaleWorker();
			updateFrameTitle();
			scheduleScaleToFit();
		}

		/**
		 * Updates the base title used by subsequent render status updates.
		 *
		 * @param title base title text
		 */
		private void setTitlePrefix(String title) {
			String normalized = title == null ? "" : title.trim();
			titlePrefix = normalized.isEmpty() ? DEFAULT_WINDOW_TITLE : normalized;
			updateFrameTitle();
		}

		/**
		 * Applies the current stable title prefix and timestamp to the frame.
		 */
		private void updateFrameTitle() {
			jframe.setTitle(titlePrefix + " - " + titleTimestamp);
		}

		/**
		 * Updates the zoom factor and schedules a rescale pass.
		 *
		 * @param zoom the new zoom factor
		 */
		private void setZoom(double zoom) {
			setZoomAround(null, zoom);
		}

		/**
		 * Updates the zoom factor while keeping an anchor point fixed on screen.
		 *
		 * @param anchor panel coordinate to zoom around, or {@code null} to use the center
		 * @param zoom the new zoom factor
		 */
		private void setZoomAround(Point anchor, double zoom) {
			double clamped = clampZoom(zoom);
			if (Math.abs(this.zoom - clamped) < 0.0001) {
				return;
			}

			int frameWidth = getWidth();
			int frameHeight = getHeight();
			Point pivot = resolveZoomPivot(anchor, frameWidth, frameHeight);
			Dimension oldFit = computeFitSize(frameWidth, frameHeight);
			double contentX = 0.5;
			double contentY = 0.5;
			if (oldFit.width > 0 && oldFit.height > 0 && frameWidth > 0 && frameHeight > 0) {
				int oldBaseX = (int) ((frameWidth - oldFit.width) / 2.0);
				int oldBaseY = (int) ((frameHeight - oldFit.height) / 2.0);
				int oldX = clampPanAxis(oldBaseX, panX, frameWidth, oldFit.width);
				int oldY = clampPanAxis(oldBaseY, panY, frameHeight, oldFit.height);
				contentX = clampDouble((pivot.x - oldX) / (double) oldFit.width, 0.0, 1.0);
				contentY = clampDouble((pivot.y - oldY) / (double) oldFit.height, 0.0, 1.0);
			}

			this.zoom = clamped;
			Dimension newFit = computeFitSize(frameWidth, frameHeight);
			if (newFit.width > 0 && newFit.height > 0 && frameWidth > 0 && frameHeight > 0) {
				int newBaseX = (int) ((frameWidth - newFit.width) / 2.0);
				int newBaseY = (int) ((frameHeight - newFit.height) / 2.0);
				int targetX = (int) Math.round(pivot.x - contentX * newFit.width);
				int targetY = (int) Math.round(pivot.y - contentY * newFit.height);
				panX = targetX - newBaseX;
				panY = targetY - newBaseY;
			}
			clampPanToBounds();
			scheduleScaleToFit();
			if (!interactionActive) {
				triggerScaleToPendingSize();
			}
			repaint();
		}

		/**
		 * Chooses the panel point that should remain fixed during zoom.
		 *
		 * @param anchor requested anchor point
		 * @param frameWidth current panel width
		 * @param frameHeight current panel height
		 * @return usable anchor point
		 */
		private Point resolveZoomPivot(Point anchor, int frameWidth, int frameHeight) {
			if (anchor != null) {
				return anchor;
			}
			return new Point(Math.max(0, frameWidth / 2), Math.max(0, frameHeight / 2));
		}

		/**
		 * Marks interaction as active and defers expensive scaling work.
		 */
		private void startInteraction() {
			interactionActive = true;
			cancelScaleWorker();
			if (interactionTimer != null) {
				interactionTimer.restart();
			}
		}

		/**
		 * Registers Ctrl/Cmd+S to open the save dialog.
		 */
		private void registerSaveShortcut() {
			int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
			KeyStroke saveStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask);
			InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
			ActionMap actionMap = getActionMap();
			inputMap.put(saveStroke, SAVE_ACTION_KEY);
			actionMap.put(SAVE_ACTION_KEY, new AbstractAction() {

				/**
				 * Serialization id for the save action.
				 */
				private static final long serialVersionUID = 1L;

				/**
				 * Opens the save dialog when the shortcut is triggered.
				 *
				 * @param e action event payload
				 */
				@Override
				public void actionPerformed(ActionEvent e) {
					showSaveDialog();
				}
			});
		}

		/**
		 * Registers Ctrl/Cmd+C to copy the current image to the clipboard.
		 *
		 * <p>Uses the platform menu shortcut mask so the binding works across OSes.</p>
		 */
		private void registerCopyShortcut() {
			int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
			KeyStroke copyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask);
			InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
			ActionMap actionMap = getActionMap();
			inputMap.put(copyStroke, COPY_ACTION_KEY);
			actionMap.put(COPY_ACTION_KEY, new AbstractAction() {

				/**
				 * Serialization id for the copy action.
				 */
				private static final long serialVersionUID = 1L;

				/**
				 * Copies the image to the clipboard when the shortcut is triggered.
				 *
				 * @param e action event payload
				 */
				@Override
				public void actionPerformed(ActionEvent e) {
					copyImageToClipboard();
				}
			});
		}

		/**
		 * Copies the current image to the system clipboard.
		 *
		 * <p>Does nothing when no image is loaded.</p>
		 */
		private void copyImageToClipboard() {
			if (image == null) {
				return;
			}
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(new ImageSelection(image), null);
		}

		/**
		 * Opens a save dialog for exporting the current image.
		 */
		private void showSaveDialog() {
			if (image == null) {
				JOptionPane.showMessageDialog(this, "No image available to save.", SAVE_DIALOG_TITLE,
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle(SAVE_DIALOG_TITLE);
			chooser.setAcceptAllFileFilterUsed(false);
			FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Image (*.png)", "png");
			FileNameExtensionFilter jpgFilter = new FileNameExtensionFilter("JPEG Image (*.jpg, *.jpeg)", "jpg", "jpeg");
			FileNameExtensionFilter bmpFilter = new FileNameExtensionFilter("Bitmap Image (*.bmp)", "bmp");
			FileNameExtensionFilter svgFilter = new FileNameExtensionFilter("SVG Image (*.svg)", "svg");
			chooser.addChoosableFileFilter(pngFilter);
			chooser.addChoosableFileFilter(jpgFilter);
			chooser.addChoosableFileFilter(bmpFilter);
			if (imageSource.supportsSvgExport()) {
				chooser.addChoosableFileFilter(svgFilter);
				chooser.setFileFilter(svgFilter);
			} else {
				chooser.setFileFilter(pngFilter);
			}
			chooser.setSelectedFile(buildDefaultSaveFile());

			int result = chooser.showSaveDialog(this);
			if (result != JFileChooser.APPROVE_OPTION) {
				return;
			}
			File selected = chooser.getSelectedFile();
			String format = resolveSaveFormat(selected, chooser.getFileFilter());
			if (format == null) {
				JOptionPane.showMessageDialog(this, "Unsupported image format.", SAVE_DIALOG_TITLE,
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			File target = ensureExtension(selected, format);
			try {
				if ("svg".equals(format)) {
					saveSvg(target);
				} else if (!ImageIO.write(image, format, target)) {
					throw new IOException("No ImageIO writer for format: " + format);
				}
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this,
						"Failed to save image: " + ex.getMessage(),
						SAVE_DIALOG_TITLE,
						JOptionPane.ERROR_MESSAGE);
			}
		}

		/**
		 * Saves the current source as a real SVG document.
		 *
		 * @param target target file
		 * @throws IOException when writing fails
		 */
		private void saveSvg(File target) throws IOException {
			if (!imageSource.supportsSvgExport()) {
				throw new IOException("The current image source cannot export SVG");
			}
			Files.writeString(target.toPath(), imageSource.renderSvg(imageWidth, imageHeight), StandardCharsets.UTF_8);
		}

		/**
		 * Builds a safe default file name for saving images.
		 *
		 * @return default file location
		 */
		private File buildDefaultSaveFile() {
			String date = Dates.getDate();
			String time = Dates.getTime().replace(":", "");
			String name = "board-" + date + "-" + time + ".png";
			return new File(name);
		}

		/**
		 * Resolve the requested image format for the given file.
		 *
		 * @param file chosen file
		 * @param filter active file filter
		 * @return normalized format string or {@code null} when unsupported
		 */
		private String resolveSaveFormat(File file, javax.swing.filechooser.FileFilter filter) {
			String ext = extensionOf(file.getName());
			if (ext != null) {
				return normalizeFormat(ext);
			}
			if (filter instanceof FileNameExtensionFilter fnFilter) {
				String[] exts = fnFilter.getExtensions();
				if (exts.length > 0) {
					return normalizeFormat(exts[0]);
				}
			}
			return null;
		}

		/**
		 * Ensure the file name ends with the chosen format extension.
		 *
		 * @param file selected file
		 * @param format normalized format
		 * @return file with an extension appended if missing
		 */
		private File ensureExtension(File file, String format) {
			String ext = extensionOf(file.getName());
			if (ext != null) {
				return file;
			}
			File parent = file.getParentFile();
			String name = file.getName() + "." + format;
			return parent == null ? new File(name) : new File(parent, name);
		}

		/**
		 * Extracts the lowercase extension from a file name.
		 *
		 * @param name file name
		 * @return extension without dot, or {@code null} when missing
		 */
		private String extensionOf(String name) {
			if (name == null) {
				return null;
			}
			int idx = name.lastIndexOf('.');
			if (idx < 0 || idx == name.length() - 1) {
				return null;
			}
			return name.substring(idx + 1).toLowerCase(Locale.ROOT);
		}

		/**
		 * Normalize image formats to the ImageIO writer names.
		 *
		 * @param ext extension value
		 * @return normalized format or {@code null} when unsupported
		 */
		private String normalizeFormat(String ext) {
			if (ext == null) {
				return null;
			}
			return switch (ext.toLowerCase(Locale.ROOT)) {
				case "png" -> "png";
				case "jpg", "jpeg" -> "jpg";
				case "bmp" -> "bmp";
				case "svg" -> "svg";
				default -> null;
			};
		}

		/**
		 * Ends the interaction window and triggers a high-quality rescale.
		 */
		private void endInteraction() {
			interactionActive = false;
			triggerScaleToPendingSize();
			repaint();
		}

		/**
		 * Applies mouse-wheel zooming on the display panel.
		 *
		 * @param event mouse wheel event to interpret as zoom deltas
		 */
		private void handleZoomWheel(MouseWheelEvent event) {
			double rotation = event.getPreciseWheelRotation();
			if (Math.abs(rotation) < 0.0001) {
				return;
			}
			startInteraction();
			double factor = Math.pow(ZOOM_STEP, -rotation);
			setZoomAround(event.getPoint(), zoom * factor);
		}

		/**
		 * Applies a pan update based on the current drag position.
		 *
		 * @param point current mouse position
		 */
		private void handlePanDrag(Point point) {
			if (lastDragPoint == null || point == null) {
				lastDragPoint = point;
				return;
			}
			int dx = point.x - lastDragPoint.x;
			int dy = point.y - lastDragPoint.y;
			if (dx == 0 && dy == 0) {
				return;
			}
			startInteraction();
			panX += dx;
			panY += dy;
			lastDragPoint = point;
			clampPanToBounds();
			scheduleScaleToFit();
			repaint();
		}

		/**
		 * Clamps the zoom value to the supported range.
		 *
		 * @param value requested zoom
		 * @return clamped zoom value
		 */
		private double clampZoom(double value) {
			if (Double.isNaN(value) || Double.isInfinite(value)) {
				return 1.0;
			}
			return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
		}

		/**
		 * Clamps the pan offsets so the image stays within the view bounds.
		 */
		private void clampPanToBounds() {
			if (image == null) {
				return;
			}
			int frameWidth = getWidth();
			int frameHeight = getHeight();
			Dimension fit = computeFitSize(frameWidth, frameHeight);
			int newWidth = fit.width;
			int newHeight = fit.height;
			if (newWidth <= 0 || newHeight <= 0) {
				return;
			}
			int baseX = (int) ((frameWidth - newWidth) / 2.0);
			int baseY = (int) ((frameHeight - newHeight) / 2.0);
			if (newWidth <= frameWidth) {
				panX = 0;
			} else {
				int minX = frameWidth - newWidth;
				int maxX = 0;
				int x = baseX + panX;
				x = clampInt(x, minX, maxX);
				panX = x - baseX;
			}
			if (newHeight <= frameHeight) {
				panY = 0;
			} else {
				int minY = frameHeight - newHeight;
				int maxY = 0;
				int y = baseY + panY;
				y = clampInt(y, minY, maxY);
				panY = y - baseY;
			}
		}

		/**
		 * Clamp an integer to the given bounds.
		 *
		 * @param value value to clamp
		 * @param min minimum inclusive
		 * @param max maximum inclusive
		 * @return clamped value
		 */
		private int clampInt(int value, int min, int max) {
			if (value < min) {
				return min;
			}
			if (value > max) {
				return max;
			}
			return value;
		}

		/**
		 * Clamp a floating-point value to the given bounds.
		 *
		 * @param value value to clamp
		 * @param min minimum inclusive
		 * @param max maximum inclusive
		 * @return clamped value
		 */
		private double clampDouble(double value, double min, double max) {
			if (value < min) {
				return min;
			}
			if (value > max) {
				return max;
			}
			return value;
		}

		/**
		 * Cancels any in-flight scaling worker task.
		 * Clears the worker reference so future scale requests can proceed.
		 */
		private void cancelScaleWorker() {
			if (scaleWorker != null && !scaleWorker.isDone()) {
				scaleWorker.cancel(true);
			}
			scaleWorker = null;
		}

		/**
		 * Computes the target size for the current frame and queues a scale request.
		 * Debounces repeated resize events to avoid excessive work.
		 */
		private void scheduleScaleToFit() {
			if (image == null) {
				return;
			}
			Dimension target = computeFitSize(getWidth(), getHeight());
			if (target.width <= 0 || target.height <= 0) {
				return;
			}
			pendingScaledWidth = target.width;
			pendingScaledHeight = target.height;
			if (resizeDebounceTimer != null) {
				resizeDebounceTimer.restart();
			}
		}

		/**
		 * Triggers scaling to the last computed target size if needed.
		 * Skips work when the cached image already matches the requested size.
		 */
		private void triggerScaleToPendingSize() {
			if (interactionActive) {
				return;
			}
			RenderState state = computeRenderState();
			if (state == null) {
				return;
			}
			if (hasMatchingCache(state)) {
				return;
			}
			startScaleWorker(state);
		}

		/**
		 * Starts a background render job for the visible region of the current zoom.
		 * Cancels any previous job and updates the cache upon completion.
		 *
		 * @param state current render state
		 */
		private void startScaleWorker(RenderState state) {
			cancelScaleWorker();
			final long requestId = ++scaleRequestId;
			final ImageSource source = imageSource;
			final int targetWidth = state.width;
			final int targetHeight = state.height;
			final int sourceX = state.sourceX();
			final int sourceY = state.sourceY();
			final int sliceWidth = state.visible.width;
			final int sliceHeight = state.visible.height;
			scaleWorker = new SwingWorker<>() {

				/**
				 * Performs the vector/raster render work off the EDT.
				 *
				 * @return newly rendered image buffer
				 */
				@Override
				protected BufferedImage doInBackground() {
					return source.renderRegion(targetWidth, targetHeight, sourceX, sourceY, sliceWidth, sliceHeight);
				}

				/**
				 * Applies the scaled image to the cache when the task completes.
				 */
				@Override
				protected void done() {
					if (isCancelled() || requestId != scaleRequestId) {
						return;
					}
					try {
						BufferedImage scaled = get();
						if (scaled == null || imageSource != source) {
							return;
						}
						scaledImageCache = scaled;
						scaledWidth = targetWidth;
						scaledHeight = targetHeight;
						cachedSourceX = sourceX;
						cachedSourceY = sourceY;
						cachedSliceWidth = sliceWidth;
						cachedSliceHeight = sliceHeight;
						updateFrameTitle();
						repaint();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (Exception ignored) {
						// If scaling fails/cancels, keep showing the last cached image.
					}
				}
			};
			scaleWorker.execute();
		}

		/**
		 * Calculates the maximum fit size that preserves the image aspect ratio.
		 * Returns a zero-size dimension when inputs are invalid.
		 *
		 * @param frameWidth available frame width in pixels
		 * @param frameHeight available frame height in pixels
		 * @return best-fit dimensions for the current image and frame
		 */
		private Dimension computeFitSize(int frameWidth, int frameHeight) {
			if (imageWidth <= 0 || imageHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) {
				return new Dimension(0, 0);
			}
			double scaleFactor = Math.min(frameWidth / (double) imageWidth, frameHeight / (double) imageHeight);
			scaleFactor *= zoom;
			int newWidth = (int) Math.round(imageWidth * scaleFactor);
			int newHeight = (int) Math.round(imageHeight * scaleFactor);
			return new Dimension(Math.max(0, newWidth), Math.max(0, newHeight));
		}

		/**
		 * Clamps a single pan axis against the available frame bounds.
		 *
		 * @param base base coordinate for centering
		 * @param pan current pan offset
		 * @param frameSize frame dimension in pixels
		 * @param contentSize content dimension in pixels
		 * @return clamped coordinate for this axis
		 */
		private int clampPanAxis(int base, int pan, int frameSize, int contentSize) {
			if (contentSize <= frameSize) {
				return base;
			}
			int min = frameSize - contentSize;
			int max = 0;
			return clampInt(base + pan, min, max);
		}

		/**
		 * Computes the visible rectangle for the image within the panel.
		 *
		 * @param x image origin x
		 * @param y image origin y
		 * @param width rendered image width
		 * @param height rendered image height
		 * @param frameWidth panel width
		 * @param frameHeight panel height
		 * @return intersection rectangle (may be empty)
		 */
		private Rectangle computeVisibleRect(int x, int y, int width, int height, int frameWidth, int frameHeight) {
			Rectangle imageRect = new Rectangle(x, y, width, height);
			Rectangle panelRect = new Rectangle(0, 0, frameWidth, frameHeight);
			return imageRect.intersection(panelRect);
		}

		/**
		 * Computes the current render state from the image and panel size.
		 *
		 * <p>Returns {@code null} when the image is absent or fully out of view.</p>
		 *
		 * @return render state snapshot, or {@code null} if nothing should be drawn
		 */
		private RenderState computeRenderState() {
			if (image == null) {
				return null;
			}
			int frameWidth = getWidth();
			int frameHeight = getHeight();
			Dimension fit = computeFitSize(frameWidth, frameHeight);
			int newWidth = fit.width;
			int newHeight = fit.height;
			if (newWidth <= 0 || newHeight <= 0) {
				return null;
			}
			int baseX = (int) ((frameWidth - newWidth) / 2.0);
			int baseY = (int) ((frameHeight - newHeight) / 2.0);
			int x = clampPanAxis(baseX, panX, frameWidth, newWidth);
			int y = clampPanAxis(baseY, panY, frameHeight, newHeight);
			Rectangle visible = computeVisibleRect(x, y, newWidth, newHeight, frameWidth, frameHeight);
			if (visible.isEmpty()) {
				return null;
			}
			return new RenderState(newWidth, newHeight, x, y, visible);
		}

		/**
		 * Paints the checkerboard background for transparent regions.
		 *
		 * @param graphics2d graphics context to draw into
		 * @param visible visible region to fill
		 */
		private void paintCheckerboard(Graphics2D graphics2d, Rectangle visible) {
			if (checkerPaint == null) {
				checkerPaint = buildCheckerPaint();
			}
			graphics2d.setPaint(checkerPaint);
			graphics2d.fillRect(visible.x, visible.y, visible.width, visible.height);
		}

		/**
		 * Checks whether the cached scaled image matches the target render size.
		 *
		 * @param state render state describing the desired size
		 * @return {@code true} when the cached image is usable
		 */
		private boolean hasMatchingCache(RenderState state) {
			return scaledImageCache != null
					&& scaledWidth == state.width
					&& scaledHeight == state.height
					&& cachedSourceX == state.sourceX()
					&& cachedSourceY == state.sourceY()
					&& cachedSliceWidth == state.visible.width
					&& cachedSliceHeight == state.visible.height;
		}

		/**
		 * Draws only the visible slice of the cached scaled image.
		 *
		 * @param graphics2d graphics context to draw into
		 * @param state render state describing the visible region
		 */
		private void drawCachedSlice(Graphics2D graphics2d, RenderState state) {
			graphics2d.drawImage(scaledImageCache, state.visible.x, state.visible.y, this);
		}

		/**
		 * Renders and draws the visible region immediately from the backing source.
		 *
		 * @param graphics2d graphics context to draw into
		 * @param state render state describing the target size
		 * @return {@code true} when the immediate render succeeded
		 */
		private boolean drawImmediateRegion(Graphics2D graphics2d, RenderState state) {
			BufferedImage rendered = imageSource.renderRegion(
					state.width,
					state.height,
					state.sourceX(),
					state.sourceY(),
					state.visible.width,
					state.visible.height);
			if (rendered == null) {
				return false;
			}
			scaledImageCache = rendered;
			scaledWidth = state.width;
			scaledHeight = state.height;
			cachedSourceX = state.sourceX();
			cachedSourceY = state.sourceY();
			cachedSliceWidth = state.visible.width;
			cachedSliceHeight = state.visible.height;
			graphics2d.drawImage(rendered, state.visible.x, state.visible.y, this);
			return true;
		}

		/**
		 * Draws the native snapshot using smooth scaling as a temporary fallback.
		 *
		 * @param graphics2d graphics context to draw into
		 * @param state render state describing the target size
		 */
		private void drawFallbackImage(Graphics2D graphics2d, RenderState state) {
			if (imageSource.renderRegionImmediately() && drawImmediateRegion(graphics2d, state)) {
				return;
			}
			graphics2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Rectangle src = scaledToNativeSourceRect(
					imageWidth,
					imageHeight,
					state.width,
					state.height,
					state.sourceX(),
					state.sourceY(),
					state.visible.width,
					state.visible.height);
			graphics2d.drawImage(image,
					state.visible.x,
					state.visible.y,
					state.visible.x + state.visible.width,
					state.visible.y + state.visible.height,
					src.x,
					src.y,
					src.x + src.width,
					src.y + src.height,
					this);
		}

		/**
		 * Paints the image, background, and border for the current render state.
		 *
		 * @param graphics2d graphics context to draw into
		 * @param state render state snapshot
		 */
		private void paintImage(Graphics2D graphics2d, RenderState state) {
			Shape oldClip = graphics2d.getClip();
			graphics2d.setClip(state.visible);
			paintCheckerboard(graphics2d, state.visible);
			if (hasMatchingCache(state)) {
				drawCachedSlice(graphics2d, state);
			} else {
				drawFallbackImage(graphics2d, state);
			}
			graphics2d.setClip(oldClip);
			paintImageBorder(graphics2d, state);
		}

		/**
		 * Paints a crisp one-pixel border around the image bounds.
		 *
		 * @param graphics2d graphics context to draw into
		 * @param state render state snapshot
		 */
		private void paintImageBorder(Graphics2D graphics2d, RenderState state) {
			graphics2d.setColor(borderColor);
			int border = IMAGE_BORDER_THICKNESS;
			int x = state.x - border;
			int y = state.y - border;
			int width = state.width + border * 2;
			int height = state.height + border * 2;
			graphics2d.fillRect(x, y, width, border);
			graphics2d.fillRect(x, y + height - border, width, border);
			graphics2d.fillRect(x, y + border, border, Math.max(0, height - border * 2));
			graphics2d.fillRect(x + width - border, y + border, border, Math.max(0, height - border * 2));
		}

		/**
		 * Paints the help HUD text near the bottom-left corner.
		 *
		 * @param graphics2d graphics context to draw into
		 */
		private void paintHelpText(Graphics2D graphics2d) {
			if (HELP_TEXT.isEmpty()) {
				return;
			}
			FontMetrics metrics = graphics2d.getFontMetrics();
			int textWidth = metrics.stringWidth(HELP_TEXT);
			int textHeight = metrics.getHeight();
			int x = HELP_PADDING;
			int y = getHeight() - HELP_PADDING;
			int baseline = y - metrics.getDescent();
			int boxX = x - 4;
			int boxY = baseline - metrics.getAscent() - 2;
			int boxWidth = textWidth + 8;
			int boxHeight = textHeight + 4;
			Color bg = new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(),
					HELP_BG_ALPHA);
			graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics2d.setColor(bg);
			graphics2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
			Color textColor = backgroundColor.equals(LIGHT_BACKGROUND)
					? new Color(40, 40, 40)
					: new Color(220, 220, 220);
			graphics2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics2d.setColor(textColor);
			graphics2d.drawString(HELP_TEXT, x, baseline);
		}

		/**
		 * Paints volatile render stats above the bottom-left help HUD.
		 *
		 * @param graphics2d graphics context to draw into
		 * @param state current render state
		 */
		private void paintStatusText(Graphics2D graphics2d, RenderState state) {
			String text = String.format(Locale.ROOT, "Size %dx%d  Zoom %.2fx", state.width, state.height, zoom);
			FontMetrics metrics = graphics2d.getFontMetrics();
			int textWidth = metrics.stringWidth(text);
			int textHeight = metrics.getHeight();
			int boxWidth = textWidth + 8;
			int boxHeight = textHeight + 4;
			int helpBoxHeight = HELP_TEXT.isEmpty() ? 0 : textHeight + 4;
			int helpBoxY = HELP_TEXT.isEmpty()
					? getHeight() - HELP_PADDING
					: getHeight() - HELP_PADDING - helpBoxHeight;
			int boxX = HELP_PADDING - 4;
			int boxY = Math.max(HELP_PADDING, helpBoxY - 4 - boxHeight);
			int baseline = boxY + 2 + metrics.getAscent();
			Color bg = new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(),
					HELP_BG_ALPHA);
			graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics2d.setColor(bg);
			graphics2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
			Color textColor = backgroundColor.equals(LIGHT_BACKGROUND)
					? new Color(40, 40, 40)
					: new Color(220, 220, 220);
			graphics2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics2d.setColor(textColor);
			graphics2d.drawString(text, boxX + 4, baseline);
		}

		/**
		 * Immutable snapshot of render geometry for a paint pass.
		 */
		private static final class RenderState {

			/**
			 * Rendered image width in pixels.
			 */
			private final int width;

			/**
			 * Rendered image height in pixels.
			 */
			private final int height;

			/**
			 * Image origin x coordinate.
			 */
			private final int x;

			/**
			 * Image origin y coordinate.
			 */
			private final int y;

			/**
			 * Visible intersection rectangle.
			 */
			private final Rectangle visible;

			/**
			 * Creates a render state container.
			 *
			 * @param width rendered image width
			 * @param height rendered image height
			 * @param x image origin x coordinate
			 * @param y image origin y coordinate
			 * @param visible visible intersection rectangle
			 */
			private RenderState(int width, int height, int x, int y, Rectangle visible) {
				this.width = width;
				this.height = height;
				this.x = x;
				this.y = y;
				this.visible = visible;
			}

			/**
			 * X coordinate of the visible region inside the scaled image.
			 *
			 * @return source x coordinate
			 */
			private int sourceX() {
				return visible.x - x;
			}

			/**
			 * Y coordinate of the visible region inside the scaled image.
			 *
			 * @return source y coordinate
			 */
			private int sourceY() {
				return visible.y - y;
			}
		}

		/**
		 * Transferable wrapper for placing an image onto the system clipboard.
		 */
		private static final class ImageSelection implements Transferable, ClipboardOwner {

			/**
			 * Image being transferred to the clipboard.
			 */
			private final Image image;

			/**
			 * Creates a selection for the provided image.
			 *
			 * @param image image to expose via clipboard
			 */
			private ImageSelection(Image image) {
				this.image = image;
			}

			/**
			 * Returns the supported transfer flavors.
			 *
			 * @return array containing {@link DataFlavor#imageFlavor}
			 */
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[] {DataFlavor.imageFlavor};
			}

			/**
			 * Reports whether a flavor is supported.
			 *
			 * @param flavor data flavor to check
			 * @return {@code true} when the flavor is {@link DataFlavor#imageFlavor}
			 */
			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return DataFlavor.imageFlavor.equals(flavor);
			}

			/**
			 * Returns the image for the requested flavor.
			 *
			 * @param flavor requested data flavor
			 * @return image payload for the flavor
			 * @throws UnsupportedFlavorException if the flavor is not supported
			 */
			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
				if (!isDataFlavorSupported(flavor)) {
					throw new UnsupportedFlavorException(flavor);
				}
				return image;
			}

			/**
			 * Notification that clipboard ownership was lost.
			 *
			 * @param clipboard clipboard that lost ownership
			 * @param contents transfer contents
			 */
			@Override
			public void lostOwnership(Clipboard clipboard, Transferable contents) {
				// no-op
			}
		}

		/**
		 * Draws the image centered and keeps the aspect ratio.
		 *
		 * <p>The method chooses cached scaled pixels when available; otherwise it
		 * falls back to on-the-fly scaling while respecting the current zoom/pan
		 * offsets and the checkerboard background.</p>
		 *
		 * @param g graphics context provided by Swing
		 */
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D graphics2d = (Graphics2D) g;
			graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			RenderState state = computeRenderState();
			if (state == null) {
				return;
			}
			paintImage(graphics2d, state);
			paintHelpText(graphics2d);
			paintStatusText(graphics2d, state);
		}
	}
}
