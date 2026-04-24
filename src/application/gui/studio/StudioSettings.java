package application.gui.studio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Persistent application-level settings for GUI v3.
 */
public final class StudioSettings {

	/**
	 * Default window width in pixels.
	 */
	private static final int DEFAULT_WIDTH = 1320;

	/**
	 * Default window height in pixels.
	 */
	private static final int DEFAULT_HEIGHT = 860;

	/**
	 * Whether the light theme is enabled.
	 */
	private boolean lightMode;

	/**
	 * Whether White is displayed at the bottom of the board.
	 */
	private boolean whiteDown;

	/**
	 * Whether side panels are hidden.
	 */
	private boolean focusMode;

	/**
	 * Stored window width in pixels.
	 */
	private int width;

	/**
	 * Stored window height in pixels.
	 */
	private int height;

	/**
	 * Constructor.
	 *
	 * @param lightMode initial light-mode flag
	 * @param whiteDown initial board orientation
	 * @param focusMode initial focus-mode flag
	 * @param width initial window width
	 * @param height initial window height
	 */
	private StudioSettings(boolean lightMode, boolean whiteDown, boolean focusMode, int width, int height) {
		this.lightMode = lightMode;
		this.whiteDown = whiteDown;
		this.focusMode = focusMode;
		this.width = width;
		this.height = height;
	}

	/**
	 * Loads settings from the v3 settings file.
	 *
	 * @param defaultLight default light-mode value
	 * @param defaultWhiteDown default board orientation
	 * @return loaded settings
	 */
	public static StudioSettings load(boolean defaultLight, boolean defaultWhiteDown) {
		StudioSettings settings = new StudioSettings(defaultLight, defaultWhiteDown, false,
				DEFAULT_WIDTH, DEFAULT_HEIGHT);
		Path path = settingsPath();
		if (!Files.exists(path)) {
			return settings;
		}
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(path)) {
			props.load(in);
			settings.lightMode = readBool(props, "lightMode", settings.lightMode);
			settings.whiteDown = readBool(props, "whiteDown", settings.whiteDown);
			settings.focusMode = readBool(props, "focusMode", settings.focusMode);
			settings.width = readInt(props, "width", settings.width);
			settings.height = readInt(props, "height", settings.height);
		} catch (IOException ignored) {
			// Corrupt settings should not prevent GUI startup.
		}
		return settings;
	}

	/**
	 * Saves settings to disk.
	 */
	public void save() {
		Properties props = new Properties();
		props.setProperty("lightMode", String.valueOf(lightMode));
		props.setProperty("whiteDown", String.valueOf(whiteDown));
		props.setProperty("focusMode", String.valueOf(focusMode));
		props.setProperty("width", String.valueOf(width));
		props.setProperty("height", String.valueOf(height));
		Path path = settingsPath();
		try {
			Files.createDirectories(path.getParent());
			try (OutputStream out = Files.newOutputStream(path)) {
				props.store(out, "ChessRTK GUI v3 settings");
			}
		} catch (IOException ignored) {
			// Best-effort settings persistence.
		}
	}

	/**
	 * Returns the settings path.
	 *
	 * @return settings path
	 */
	public static Path settingsPath() {
		return Paths.get(System.getProperty("user.home"), ".crtk", "gui-v3.properties");
	}

	/**
	 * Reads a boolean property with a fallback.
	 *
	 * @param props settings properties
	 * @param key property key
	 * @param fallback fallback value
	 * @return parsed boolean or fallback
	 */
	private static boolean readBool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	/**
	 * Reads an integer property with a fallback.
	 *
	 * @param props settings properties
	 * @param key property key
	 * @param fallback fallback value
	 * @return parsed integer or fallback
	 */
	private static int readInt(Properties props, String key, int fallback) {
		try {
			return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)));
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	/**
	 * Returns whether the light theme is active.
	 *
	 * @return true for light mode
	 */
	public boolean isLightMode() {
		return lightMode;
	}

	/**
	 * Sets whether the light theme is active.
	 *
	 * @param lightMode true for light mode
	 */
	public void setLightMode(boolean lightMode) {
		this.lightMode = lightMode;
	}

	/**
	 * Returns whether White is displayed at the bottom of the board.
	 *
	 * @return true when White is down
	 */
	public boolean isWhiteDown() {
		return whiteDown;
	}

	/**
	 * Sets whether White is displayed at the bottom of the board.
	 *
	 * @param whiteDown true when White is down
	 */
	public void setWhiteDown(boolean whiteDown) {
		this.whiteDown = whiteDown;
	}

	/**
	 * Returns whether focus mode hides side panels.
	 *
	 * @return true when focus mode is active
	 */
	public boolean isFocusMode() {
		return focusMode;
	}

	/**
	 * Sets whether focus mode hides side panels.
	 *
	 * @param focusMode true to enable focus mode
	 */
	public void setFocusMode(boolean focusMode) {
		this.focusMode = focusMode;
	}

	/**
	 * Returns the stored window width.
	 *
	 * @return window width in pixels
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Sets the stored window width with a minimum bound.
	 *
	 * @param width requested window width in pixels
	 */
	public void setWidth(int width) {
		this.width = Math.max(900, width);
	}

	/**
	 * Returns the stored window height.
	 *
	 * @return window height in pixels
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Sets the stored window height with a minimum bound.
	 *
	 * @param height requested window height in pixels
	 */
	public void setHeight(int height) {
		this.height = Math.max(640, height);
	}
}
