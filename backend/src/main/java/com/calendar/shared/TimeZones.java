package com.calendar.shared;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class TimeZones {

	private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private static final List<Integer> WEEKEND = List.of(0, 6);

	private static final int MINUTES_PER_HOUR = 60;

	public record Status(boolean withinWorkingHours, String localDate, int localStartMinute, int localEndMinute,
			boolean workingDay) {
	}

	public static boolean isTimeZone(String value) {
		try {
			ZoneId.of(value);

			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public static String addCalendarDays(String dateKey, int amount) {
		return LocalDate.parse(dateKey).plusDays(amount).format(DATE_KEY);
	}

	public static int dayOfWeek(String dateKey) {
		return LocalDate.parse(dateKey).getDayOfWeek().getValue() % 7;
	}

	public static boolean isWeekend(String dateKey) {
		return WEEKEND.contains(dayOfWeek(dateKey));
	}

	public static String localDateKey(Instant value, String timeZone) {
		return value.atZone(ZoneId.of(timeZone)).toLocalDate().format(DATE_KEY);
	}

	public static int minuteOfDay(Instant value, String timeZone) {
		ZonedDateTime local = value.atZone(ZoneId.of(timeZone));

		return local.getHour() * MINUTES_PER_HOUR + local.getMinute();
	}

	public static Instant zonedDateTime(String dateKey, int minuteOfDay, String timeZone) {
		return LocalDate.parse(dateKey).atTime(LocalTime.MIDNIGHT).plusMinutes(minuteOfDay).atZone(ZoneId.of(timeZone))
				.toInstant();
	}

	public static Status workingHoursStatus(Instant startAt, Instant endAt, int startMinute, int endMinute,
			String timeZone) {
		String startDate = localDateKey(startAt, timeZone);
		String endDate = localDateKey(endAt, timeZone);
		int localStartMinute = minuteOfDay(startAt, timeZone);
		int localEndMinute = minuteOfDay(endAt, timeZone);
		boolean workingDay = !isWeekend(startDate);
		boolean within = startDate.equals(endDate) && workingDay && localStartMinute >= startMinute
				&& localEndMinute <= endMinute;

		return new Status(within, startDate, localStartMinute, localEndMinute, workingDay);
	}

	private TimeZones() {
	}
}
