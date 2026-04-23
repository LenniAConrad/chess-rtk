package application.gui.studio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

/**
 * Runs CRTK tasks as cancellable subprocesses.
 */
public final class StudioTaskRunner {

	private SwingWorker<Integer, String> worker;
	private Process process;

	/**
	 * Starts a task.
	 *
	 * @param args CRTK args after executable
	 * @param onOutput output callback
	 * @param onDone completion callback
	 */
	public void start(List<String> args, Consumer<String> onOutput, Consumer<Integer> onDone) {
		cancel();
		worker = new SwingWorker<>() {
			@Override
			protected Integer doInBackground() throws Exception {
				List<String> command = new ArrayList<>();
				command.add(System.getProperty("java.home") + java.io.File.separator + "bin"
						+ java.io.File.separator + "java");
				command.add("-cp");
				command.add(System.getProperty("java.class.path"));
				command.add("application.Main");
				command.addAll(args);
				process = new ProcessBuilder(command).redirectErrorStream(true).start();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(
						process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null && !isCancelled()) {
						publish(line + System.lineSeparator());
					}
				}
				return process.waitFor();
			}

			@Override
			protected void process(List<String> chunks) {
				if (onOutput != null) {
					for (String chunk : chunks) {
						onOutput.accept(chunk);
					}
				}
			}

			@Override
			protected void done() {
				int code = -1;
				try {
					code = isCancelled() ? -1 : get();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					code = -1;
				} catch (Exception ignored) {
					code = -1;
				}
				if (onDone != null) {
					onDone.accept(code);
				}
			}
		};
		worker.execute();
	}

	/**
	 * Cancels the current task.
	 */
	public void cancel() {
		if (worker != null && !worker.isDone()) {
			worker.cancel(true);
		}
		if (process != null && process.isAlive()) {
			process.destroy();
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		process = null;
	}

	/**
	 * Returns whether a task is running.
	 *
	 * @return true when running
	 */
	public boolean isRunning() {
		return worker != null && !worker.isDone();
	}
}
