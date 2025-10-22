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
	 * Used for converting a {@code Date} into the format of a year.
	 */
	private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("y");

	/**
	 * Used for converting a {@code Date} into the format of a month.
	 */
	private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");

	/**
	 * Used for converting a {@code Date} into the format of a day.
	 */
	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

	/**
	 * Used for converting a {@code Date} into the format of hour-minute-second.
	 */
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	/**
	 * Used for converting a {@code Date} into the format of a time zone offset.
	 */
	private static final DateTimeFormatter TIMEZONE_FORMAT = DateTimeFormatter.ofPattern("XXX");

	/**
	 * This is a utility Class. Don't let anyone instantiate it.
	 */
	private Dates() {
		
	}
	
	/**
	 * Returns a time stamp {@code String} in the format "yyyy-MM-dd HH:mm:ss XXX"
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
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getTimestamp() {
		return getTimestamp(new Date());
	}

	/**
	 * Returns a time stamp {@code String} in the format "yyyy-MM-dd HH:mm:ss XXX".
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
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
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
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
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
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getDate(Date date) {
		return format(DATE_FORMAT, date);
	}
	
	/**
	 * Returns the year of the given date as a {@code String} of the current time.
	 * 
	 * @return The year of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getYear() {
		return getYear(new Date());
	}

	/**
	 * Returns the year of the given date as a {@code String}.
	 * 
	 * @return The year of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getYear(Date date) {
		return format(YEAR_FORMAT, date);
	}

	/**
	 * Returns the month of the given date as a {@code String} of the current time.
	 * 
	 * @return The month of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getYear()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getMonth() {
		return getMonth(new Date());
	}

	/**
	 * Returns the month of the given date as a {@code String}.
	 * 
	 * @param The date to extract the month from
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getYear()
	 * @see getDay()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getMonth(Date date) {
		return format(MONTH_FORMAT, date);
	}

	/**
	 * Returns the day of the given date as a {@code String} of the current time.
	 * 
	 * @return The day of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getYear()
	 * @see getMonth()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getDay() {
		return getDay(new Date());
	}

	/**
	 * Returns the day of the given date as a {@code String}.
	 * 
	 * @param The date to extract the day from
	 * @return The day of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getYear()
	 * @see getMonth()
	 * @see getTime()
	 * @see getTimeZone()
	 */
	public static String getDay(Date date) {
		return format(DAY_FORMAT, date);
	}

	/**
	 * Returns the time of the given date as a {@code String} in the format
	 * "HH:mm:ss" of the current time.
	 * 
	 * @return The time of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTimeZone()
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
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTimeZone()
	 */
	public static String getTime(Date date) {
		return format(TIME_FORMAT, date);
	}

	/**
	 * Used for retrieving the current time zone of the given date as a
	 * {@code String} in ISO 8601 format of the current time.
	 * 
	 * @return The time zone of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 */
	public static String getTimezone() {
		return getTimezone(new Date());
	}

	/**
	 * Used for retrieving the current time zone of the given date as a
	 * {@code String} in ISO 8601 format.
	 * 
	 * @param The date to extract the time zone from
	 * @return The time zone of the given date as a {@code String}
	 * @see getTimestamp()
	 * @see getDate()
	 * @see getYear()
	 * @see getMonth()
	 * @see getDay()
	 * @see getTime()
	 */
	public static String getTimezone(Date date) {
		return format(TIMEZONE_FORMAT, date);
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