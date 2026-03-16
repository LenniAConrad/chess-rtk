package application.gui.history.fen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Manages a list of FEN strings plus the current index/source metadata.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class FenListManager {

	/**
	 * entries field.
	 */
	private final List<String> entries = new ArrayList<>();
	/**
	 * random field.
	 */
	private final Random random = new Random();
	/**
	 * index field.
	 */
	private int index = -1;
	/**
	 * sourceLabel field.
	 */
	private String sourceLabel = "";
	/**
	 * sourcePath field.
	 */
	private Path sourcePath;

	/**
	 * Clears the list and resets metadata.
	 */
	public void clear() {
		entries.clear();
		index = -1;
		sourceLabel = "";
		sourcePath = null;
	}

	/**
	 * Loads FEN strings.
	 */
	public boolean load(List<String> lines, String label) {
		entries.clear();
		if (lines != null) {
			for (String line : lines) {
				if (line != null && !line.isBlank()) {
					entries.add(line.trim());
				}
			}
		}
		if (entries.isEmpty()) {
			index = -1;
			sourceLabel = "";
			return false;
		}
		index = 0;
		sourceLabel = label != null ? label : "";
		return true;
	}

	/**
	 * entries method.
	 *
	 * @return return value.
	 */
	public List<String> entries() {
		return Collections.unmodifiableList(entries);
	}

	/**
	 * size method.
	 *
	 * @return return value.
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * hasEntries method.
	 *
	 * @return return value.
	 */
	public boolean hasEntries() {
		return !entries.isEmpty();
	}

	/**
	 * getIndex method.
	 *
	 * @return return value.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * setIndex method.
	 *
	 * @param newIndex parameter.
	 */
	public void setIndex(int newIndex) {
		if (entries.isEmpty()) {
			index = -1;
			return;
		}
		index = Math.max(0, Math.min(newIndex, entries.size() - 1));
	}

	/**
	 * currentFen method.
	 *
	 * @return return value.
	 */
	public String currentFen() {
		return getEntry(index);
	}

	/**
	 * getEntry method.
	 *
	 * @param idx parameter.
	 * @return return value.
	 */
	public String getEntry(int idx) {
		if (idx < 0 || idx >= entries.size()) {
			return null;
		}
		return entries.get(idx);
	}

	/**
	 * next method.
	 *
	 * @return return value.
	 */
	public String next() {
		if (!hasEntries()) {
			return null;
		}
		if (index >= entries.size() - 1) {
			index = entries.size() - 1;
		} else {
			index++;
		}
		return currentFen();
	}

	/**
	 * prev method.
	 *
	 * @return return value.
	 */
	public String prev() {
		if (!hasEntries()) {
			return null;
		}
		if (index <= 0) {
			index = 0;
		} else {
			index--;
		}
		return currentFen();
	}

	/**
	 * random method.
	 *
	 * @return return value.
	 */
	public String random() {
		if (!hasEntries()) {
			return null;
		}
		index = random.nextInt(entries.size());
		return currentFen();
	}

	/**
	 * jump method.
	 *
	 * @param oneBased parameter.
	 * @return return value.
	 */
	public String jump(int oneBased) {
		if (!hasEntries()) {
			return null;
		}
		int target = Math.max(1, Math.min(oneBased, entries.size())) - 1;
		index = target;
		return currentFen();
	}

	/**
	 * sourceLabel method.
	 *
	 * @return return value.
	 */
	public String sourceLabel() {
		return sourceLabel;
	}

	/**
	 * setSourceLabel method.
	 *
	 * @param label parameter.
	 */
	public void setSourceLabel(String label) {
		sourceLabel = label != null ? label : "";
	}

	/**
	 * sourcePath method.
	 *
	 * @return return value.
	 */
	public Path sourcePath() {
		return sourcePath;
	}

	/**
	 * setSourcePath method.
	 *
	 * @param path parameter.
	 */
	public void setSourcePath(Path path) {
		sourcePath = path;
	}
}
