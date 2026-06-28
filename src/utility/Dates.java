package utility;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Used for providing utility methods for working with dates and times.
 *
 * @author Lennart A. Conrad
 */
public class Dates {

	/**
	 * Used for converting a {@code Date} into the format of a time stamp.
	 */
	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

	/**
	 * Used for converting a {@code Date} into the format of a date.
	 */
	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * Used for converting a {@code Date} into the format of hour-minute-second.
	 */
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	/**
	 * This is a utility Class. Don't let anyone instantiate it.
	 */
	private Dates() {

	}

	/**
	 * Returns a time stamp {@code String} in the format {@code yyyy-MM-dd HH:mm:ss}
	 * with an ISO-8601 zone offset (for example {@code +02:00}) appended,
	 * of the current time.
	 *
	 * <p>
	 * Example
	 * <p>
	 *
	 * <blockquote> "2023-08-13 01:08:41 +02:00". </blockquote>
	 *
	 * @return A {@code String} representing the current time stamp
	 * @see getDate()
	 * @see getTime()
	 */
	public static String getTimestamp() {
		return getTimestamp(new Date());
	}

	/**
	 * Returns a time stamp {@code String} in the format {@code yyyy-MM-dd HH:mm:ss}
	 * with an ISO-8601 zone offset (for example {@code +02:00}) appended.
	 *
	 * <p>
	 * Example
	 * <p>
	 *
	 * <blockquote> "2023-08-13 01:08:41 +02:00". </blockquote>
	 *
	 * @param date the date to extract the time stamp from
	 * @return A {@code String} representing the current time stamp
	 * @see getDate()
	 * @see getTime()
	 */
	public static String getTimestamp(Date date) {
		return format(TIMESTAMP_FORMAT, date);
	}


	/**
	 * Returns a date {@code String} in the format "yyyy-MM-dd".
	 *
	 * <p>
	 * Example
	 * <p>
	 *
	 * <blockquote> "2024-08-12". </blockquote>
	 *
	 * @param date the date to extract the date from
	 * @return A {@code String} representing the current date
	 * @see getTimestamp()
	 * @see getTime()
	 */
	public static String getDate() {
		return getDate(new Date());
	}

	/**
	 * Returns a date {@code String} in the format "yyyy-MM-dd".
	 *
	 * <p>
	 * Example
	 * <p>
	 *
	 * <blockquote> "2024-08-12". </blockquote>
	 *
	 * @param date the date to extract the date from
	 * @return A {@code String} representing the current date
	 * @see getTimestamp()
	 * @see getTime()
	 */
	public static String getDate(Date date) {
		return format(DATE_FORMAT, date);
	}

	/**
	 * Returns the time of the given date as a {@code String} in the format
	 * "HH:mm:ss" of the current time.
	 *
	 * @return The time of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 */
	public static String getTime() {
		return getTime(new Date());
	}

	/**
	 * Returns the time of the given date as a {@code String} in the format
	 * "HH:mm:ss".
	 *
	 * @param The date to extract the time from
	 * @return The time of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @param date date to format or parse
	 */
	public static String getTime(Date date) {
		return format(TIME_FORMAT, date);
	}

	/**
	 * Used for formatting a {@code Date} with the provided {@link DateTimeFormatter}.
	 *
	 * @param formatter the formatter to use
	 * @param date      the date to convert
	 * @return the formatted string
	 */
	private static String format(DateTimeFormatter formatter, Date date) {
		return formatter.format(
				ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
	}
}
