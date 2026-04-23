package application.gui.studio;

import java.util.List;

/**
 * A goal-oriented CRTK task.
 *
 * @param id stable id
 * @param label user-facing label
 * @param group command group label
 * @param description short description
 * @param baseArgs CRTK command arguments without executable
 * @param supportsFen whether current FEN should be injected
 * @param advanced whether task is advanced/heavyweight
 */
public record StudioTask(
		String id,
		String label,
		String group,
		String description,
		List<String> baseArgs,
		boolean supportsFen,
		boolean advanced) {
}
