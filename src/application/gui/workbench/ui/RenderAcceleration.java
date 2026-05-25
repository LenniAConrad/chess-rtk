package application.gui.workbench.ui;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.ImageCapabilities;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.Locale;

/**
 * Workbench rendering-acceleration helpers for Java2D-heavy Swing surfaces.
 *
 * <p>
 * The class keeps GPU rendering opt-in scoped to the workbench command, so
 * normal CLI commands do not initialise AWT pipelines or pull GUI state into a
 * quick terminal evaluation. Heavy custom painters can also request display
 * compatible images here, allowing Java2D to manage those bitmaps in the active
 * graphics pipeline when the platform supports it.
 * </p>
 *
 * @author Lennart A. Conrad
 */
public final class RenderAcceleration {

    /**
     * Environment variable that disables automatic Java2D pipeline requests.
     */
    private static final String ENV_RENDER_GPU = "CRTK_RENDER_GPU";

    /**
     * Java2D OpenGL pipeline property.
     */
    private static final String PROP_OPENGL = "sun.java2d.opengl";

    /**
     * Java2D Metal pipeline property.
     */
    private static final String PROP_METAL = "sun.java2d.metal";

    /**
     * Java2D Direct3D pipeline property.
     */
    private static final String PROP_D3D = "sun.java2d.d3d";

    /**
     * Java2D XRender pipeline property.
     */
    private static final String PROP_XRENDER = "sun.java2d.xrender";

    /**
     * Java2D offscreen pixmap hint used by managed images.
     */
    private static final String PROP_OFFSCREEN = "sun.java2d.pmoffscreen";

    /**
     * AWT background erase flag; avoids redundant clears before Swing repaints.
     */
    private static final String PROP_NO_ERASE = "sun.awt.noerasebackground";

    /**
     * Human-readable state when no graphics device exists.
     */
    private static final String PIPELINE_HEADLESS = "headless";

    /**
     * Prevents instantiation.
     */
    private RenderAcceleration() {
        // utility
    }

    /**
     * Requests the best Java2D GPU pipeline for the current platform.
     *
     * <p>
     * Explicit user-provided {@code -Dsun.java2d.*} settings are preserved.
     * Setting {@code CRTK_RENDER_GPU=0} or {@code CRTK_RENDER_GPU=false} skips
     * all automatic pipeline changes.
     * </p>
     */
    public static void installForWorkbench() {
        if (GraphicsEnvironment.isHeadless() || disabledByEnvironment()) {
            return;
        }
        setDefault(PROP_OFFSCREEN, "true");
        setDefault(PROP_NO_ERASE, "true");

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            setDefault(PROP_METAL, "true");
        } else if (os.contains("win")) {
            setDefault(PROP_D3D, "true");
        } else {
            setDefault(PROP_OPENGL, "true");
            setDefault(PROP_XRENDER, "true");
        }
    }

    /**
     * Creates a display-compatible image when a graphics device is available,
     * otherwise falls back to a normal ARGB/RGB software image.
     *
     * @param width requested image width
     * @param height requested image height
     * @param transparency {@link Transparency} mode
     * @return compatible buffered image
     */
    public static BufferedImage compatibleImage(int width, int height, int transparency) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        GraphicsConfiguration configuration = graphicsConfiguration();
        if (configuration != null) {
            return configuration.createCompatibleImage(safeWidth, safeHeight, transparency);
        }
        int type = transparency == Transparency.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        return new BufferedImage(safeWidth, safeHeight, type);
    }

    /**
     * Creates a translucent display-compatible image.
     *
     * @param width requested image width
     * @param height requested image height
     * @return translucent compatible image
     */
    public static BufferedImage translucentImage(int width, int height) {
        return compatibleImage(width, height, Transparency.TRANSLUCENT);
    }

    /**
     * Creates an opaque display-compatible image.
     *
     * @param width requested image width
     * @param height requested image height
     * @return opaque compatible image
     */
    public static BufferedImage opaqueImage(int width, int height) {
        return compatibleImage(width, height, Transparency.OPAQUE);
    }

    /**
     * Returns the current Java2D rendering status.
     *
     * @return render status snapshot
     */
    public static RenderStatus status() {
        if (GraphicsEnvironment.isHeadless()) {
            return new RenderStatus(false, false, false,
                    PIPELINE_HEADLESS, "no graphics device");
        }
        String pipeline = requestedPipeline();
        GraphicsConfiguration configuration = graphicsConfiguration();
        if (configuration == null) {
            return new RenderStatus(pipelineRequested(), false, false,
                    pipeline, "no graphics configuration");
        }
        boolean compatible = compatibleImageAccelerated(configuration);
        boolean volatileAccelerated = volatileImageAccelerated(configuration);
        String detail = volatileAccelerated
                ? "volatile images accelerated"
                : compatible ? "compatible images managed"
                        : "software fallback";
        return new RenderStatus(pipelineRequested(), compatible,
                volatileAccelerated, pipeline, detail);
    }

    /**
     * Returns a concise human-readable status for UI diagnostics.
     *
     * @return status text
     */
    public static String summary() {
        RenderStatus status = status();
        if (PIPELINE_HEADLESS.equals(status.pipeline())) {
            return "headless test mode";
        }
        String request = status.gpuRequested() ? status.pipeline() + " requested" : "default pipeline";
        return request + " - " + status.detail();
    }

    /**
     * Returns the default graphics configuration, or null in headless/failing
     * environments.
     *
     * @return graphics configuration, or null
     */
    private static GraphicsConfiguration graphicsConfiguration() {
        try {
            GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice device = environment.getDefaultScreenDevice();
            return device == null ? null : device.getDefaultConfiguration();
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    /**
     * Returns whether a compatible buffered image reports acceleration.
     *
     * @param configuration graphics configuration
     * @return true when image capabilities report acceleration
     */
    private static boolean compatibleImageAccelerated(GraphicsConfiguration configuration) {
        BufferedImage image = configuration.createCompatibleImage(16, 16, Transparency.TRANSLUCENT);
        try {
            ImageCapabilities capabilities = image.getCapabilities(configuration);
            return capabilities != null && capabilities.isAccelerated();
        } finally {
            image.flush();
        }
    }

    /**
     * Returns whether the default volatile image path reports acceleration.
     *
     * @param configuration graphics configuration
     * @return true when volatile images report acceleration
     */
    private static boolean volatileImageAccelerated(GraphicsConfiguration configuration) {
        VolatileImage image = null;
        try {
            image = configuration.createCompatibleVolatileImage(16, 16, Transparency.TRANSLUCENT);
            ImageCapabilities capabilities = image.getCapabilities();
            return capabilities != null && capabilities.isAccelerated();
        } catch (RuntimeException | LinkageError ex) {
            return false;
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    /**
     * Sets a system property only when the user did not provide one.
     *
     * @param key property key
     * @param value property value
     */
    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    /**
     * Returns whether automatic rendering acceleration is disabled by the
     * environment.
     *
     * @return true when disabled
     */
    private static boolean disabledByEnvironment() {
        String value = System.getenv(ENV_RENDER_GPU);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "0".equals(normalized)
                || "false".equals(normalized)
                || "no".equals(normalized)
                || "off".equals(normalized);
    }

    /**
     * Returns whether any GPU pipeline has been requested.
     *
     * @return true when a GPU pipeline property is true
     */
    private static boolean pipelineRequested() {
        return isTrue(PROP_METAL)
                || isTrue(PROP_D3D)
                || isTrue(PROP_OPENGL)
                || isTrue(PROP_XRENDER);
    }

    /**
     * Returns a display label for the requested Java2D pipeline.
     *
     * @return pipeline label
     */
    private static String requestedPipeline() {
        if (isTrue(PROP_METAL)) {
            return "Metal";
        }
        if (isTrue(PROP_D3D)) {
            return "Direct3D";
        }
        if (isTrue(PROP_OPENGL)) {
            return "OpenGL";
        }
        if (isTrue(PROP_XRENDER)) {
            return "XRender";
        }
        return "default";
    }

    /**
     * Returns whether a boolean system property is enabled.
     *
     * @param key property key
     * @return true when property value is true
     */
    private static boolean isTrue(String key) {
        return Boolean.parseBoolean(System.getProperty(key, "false"));
    }

    /**
     * Java2D rendering acceleration status.
     *
     * @param gpuRequested true when a GPU pipeline was requested
     * @param compatibleImageAccelerated true when compatible images report
     *     acceleration
     * @param volatileImageAccelerated true when volatile images report
     *     acceleration
     * @param pipeline requested pipeline label
     * @param detail concise diagnostic detail
     */
    public record RenderStatus(
            boolean gpuRequested,
            boolean compatibleImageAccelerated,
            boolean volatileImageAccelerated,
            String pipeline,
            String detail) {
    }
}
