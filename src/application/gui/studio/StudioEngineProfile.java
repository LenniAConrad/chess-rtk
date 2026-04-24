package application.gui.studio;

import java.nio.file.Path;

/**
 * Named engine profile backed by a protocol TOML path.
 *
 * @param name profile name
 * @param path protocol path
 */
public record StudioEngineProfile(String name, Path path) {

	/**
	 * Default profile.
	 *
	 * @return default profile
	 */
	public static StudioEngineProfile defaultProfile() {
		return new StudioEngineProfile("Default", Path.of("config", "default.engine.toml"));
	}

	/**
	 * Formats the profile label shown in GUI controls.
	 *
	 * @return profile label
	 */
	@Override
	public String toString() {
		return name + " (" + path + ")";
	}
}
