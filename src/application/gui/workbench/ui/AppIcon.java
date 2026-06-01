package application.gui.workbench.ui;

import chess.images.assets.Pictures;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

/**
 * Application icon loader for the Swing workbench.
 */
public final class AppIcon {

    /**
     * Logger for non-fatal icon loading failures.
     */
    private static final Logger LOGGER = Logger.getLogger(AppIcon.class.getName());

    /**
     * Desktop shell application id / WM_CLASS used to match the window to the
     * installed desktop entry.
     */
    public static final String DESKTOP_APP_ID = "crtk-workbench";

    /**
     * Human-readable desktop application name.
     */
    public static final String DESKTOP_APP_NAME = "ChessRTK Workbench";

    /**
     * Undocumented but widely used OpenJDK property for X11 WM_CLASS.
     */
    private static final String X11_WM_CLASS_PROPERTY = "sun.awt.X11.XWMClass";

    /**
     * macOS app-menu/dock application-name property.
     */
    private static final String MAC_APP_NAME_PROPERTY = "apple.awt.application.name";

    /**
     * macOS system menu-bar property used by Swing/AWT.
     */
    private static final String MAC_SCREEN_MENU_BAR_PROPERTY = "apple.laf.useScreenMenuBar";

    /**
     * Repository-relative PNG icon asset used by desktop launchers.
     */
    private static final Path ASSET_PATH = Path.of("assets", "logo", "app", "crtk-chemical-board.png");

    /**
     * Classpath fallback path when assets are bundled by an external package.
     */
    private static final String RESOURCE_PATH = "/assets/logo/app/crtk-chemical-board.png";

    /**
     * Native window icon sizes requested from the source artwork.
     */
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

    /**
     * Prevents instantiation.
     */
    private AppIcon() {
        // utility
    }

    /**
     * Returns the repository-relative PNG app icon path.
     *
     * @return icon asset path
     */
    public static Path assetPath() {
        return ASSET_PATH;
    }

    /**
     * Installs process-level desktop identity properties before Swing creates a
     * native peer.
     */
    public static void installDesktopProperties() {
        setDefaultProperty(X11_WM_CLASS_PROPERTY, DESKTOP_APP_ID);
        setDefaultProperty(MAC_APP_NAME_PROPERTY, DESKTOP_APP_NAME);
        setDefaultProperty(MAC_SCREEN_MENU_BAR_PROPERTY, "true");
    }

    /**
     * Applies the ChessRTK app icon variants to a Swing frame.
     *
     * @param frame frame receiving the app icon
     */
    public static void applyTo(JFrame frame) {
        Objects.requireNonNull(frame, "frame");
        installDesktopProperties();
        List<Image> images = iconImages();
        frame.setName(DESKTOP_APP_ID);
        frame.setIconImages(images);
        applyTaskbarIcon(images.get(images.size() - 1));
    }

    /**
     * Loads app icon variants for native window managers.
     *
     * @return icon variants from small tray sizes through source resolution
     */
    public static List<Image> iconImages() {
        BufferedImage source = sourceIcon();
        List<Image> icons = new ArrayList<>(ICON_SIZES.length + 1);
        for (int size : ICON_SIZES) {
            icons.add(scaleImage(source, size, size));
        }
        icons.add(source);
        return List.copyOf(icons);
    }

    /**
     * Loads the icon source image or renders the built-in fallback.
     *
     * @return source icon image
     */
    private static BufferedImage sourceIcon() {
        BufferedImage image = readIconAsset();
        if (image != null) {
            return image;
        }
        return copyImage(Pictures.Logo);
    }

    /**
     * Reads the PNG icon from the filesystem or classpath.
     *
     * @return decoded icon image, or {@code null} when unavailable
     */
    private static BufferedImage readIconAsset() {
        if (Files.isRegularFile(ASSET_PATH)) {
            try (InputStream stream = Files.newInputStream(ASSET_PATH)) {
                return ImageIO.read(stream);
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "Unable to read app icon asset", ex);
            }
        }
        try (InputStream stream = AppIcon.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream != null) {
                return ImageIO.read(stream);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Unable to read app icon resource", ex);
        }
        return null;
    }

    /**
     * Sets a system property only when the launcher/user has not already set it.
     *
     * @param key property key
     * @param value default value
     */
    private static void setDefaultProperty(String key, String value) {
        if (System.getProperty(key) == null || System.getProperty(key).isBlank()) {
            System.setProperty(key, value);
        }
    }

    /**
     * Applies the icon to the platform taskbar/dock when the runtime supports it.
     *
     * @param image source image
     */
    private static void applyTaskbarIcon(Image image) {
        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(image);
            }
        } catch (UnsupportedOperationException | SecurityException ex) {
            LOGGER.log(Level.FINE, "Unable to apply taskbar app icon", ex);
        }
    }

    /**
     * Copies a source image into an ARGB image.
     *
     * @param source source image
     * @return copied image
     */
    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    /**
     * Scales an image with high-quality interpolation.
     *
     * @param source source image
     * @param width target width
     * @param height target height
     * @return scaled image
     */
    private static BufferedImage scaleImage(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            installQualityRendering(graphics);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    /**
     * Installs anti-aliasing and interpolation hints on a graphics context.
     *
     * @param graphics graphics context
     */
    private static void installQualityRendering(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
