package application.cli;

/**
 * Validation helpers used throughout the CLI to guard numeric options.
 *
 * <p>Each helper prints a descriptive error message and terminates the JVM
 * with {@code 2} when its contract is violated so calling code can assume
 * valid values afterwards.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Validation {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Validation() {
		// utility
	}

	/**
	 * Ensures the supplied option value is strictly positive.
	 *
	 * @param cmd   label of the invoking command (used in diagnostics)
	 * @param opt   name of the option being validated
	 * @param value value provided by the user
	 */
	public static void requirePositive(String cmd, String opt, int value) {
		if (value <= 0) {
			System.err.printf("%s: %s must be positive%n", cmd, opt);
			System.exit(2);
		}
	}

	/**
	 * Ensures the supplied option value is zero or greater.
	 *
	 * @param cmd   label of the invoking command (used in diagnostics)
	 * @param opt   name of the option being validated
	 * @param value value provided by the user
	 */
	public static void requireNonNegative(String cmd, String opt, int value) {
		if (value < 0) {
			System.err.printf("%s: %s must be non-negative%n", cmd, opt);
			System.exit(2);
		}
	}

	/**
	 * Ensures the supplied option value falls between the given inclusive bounds.
	 *
	 * @param cmd   label of the invoking command (used in diagnostics)
	 * @param opt   name of the option being validated
	 * @param value value provided by the user
	 * @param min   lower bound (inclusive)
	 * @param max   upper bound (inclusive)
	 */
	public static void requireBetweenInclusive(String cmd, String opt, int value, int min, int max) {
		if (value < min || value > max) {
			System.err.printf("%s: %s must be between %d and %d%n", cmd, opt, min, max);
			System.exit(2);
		}
	}
}
