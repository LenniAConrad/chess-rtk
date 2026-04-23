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

	private static final int DEFAULT_WIDTH = 1320;
	private static final int DEFAULT_HEIGHT = 860;

	private boolean lightMode;
	private boolean whiteDown;
	private boolean focusMode;
	private int width;
	private int height;

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

	private static boolean readBool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	private static int readInt(Properties props, String key, int fallback) {
		try {
			return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)));
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	public boolean isLightMode() {
		return lightMode;
	}

	public void setLightMode(boolean lightMode) {
		this.lightMode = lightMode;
	}

	public boolean isWhiteDown() {
		return whiteDown;
	}

	public void setWhiteDown(boolean whiteDown) {
		this.whiteDown = whiteDown;
	}

	public boolean isFocusMode() {
		return focusMode;
	}

	public void setFocusMode(boolean focusMode) {
		this.focusMode = focusMode;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = Math.max(900, width);
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = Math.max(640, height);
	}
}
