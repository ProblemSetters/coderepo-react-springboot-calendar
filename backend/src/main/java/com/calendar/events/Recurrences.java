package com.calendar.events;

import java.text.Collator;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.calendar.shared.TimeZones;

public final class Recurrences {

	public record ResponseState(String status, Instant respondedAt) {
	}

	private static final List<String> REPEATING = List.of("daily", "weekly", "monthly", "yearly", "weekdays");

	private static final int DAYS_PER_WEEK = 7;

	private static final Collator TITLE_ORDER = Collator.getInstance(Locale.US);

	public static boolean isRecurring(Event event) {
		String frequency = event.getRecurrence().getFrequency();

		return frequency != null && !"none".equals(frequency);
	}

	public static List<String> repeatingFrequencies() {
		return REPEATING;
	}

	public static String recurrenceUntilDate(Instant until) {
		return until.atZone(ZoneOffset.UTC).toLocalDate().toString();
	}

	public static List<Occurrence> expandEvents(List<Event> events, Instant from, Instant to) {
		List<Occurrence> occurrences = new ArrayList<>();

		for (Event event : events) {
			occurrences.addAll(expandEvent(event, from, to));
		}

		occurrences.sort(Comparator.comparing(Occurrence::startAt)
				.thenComparing(occurrence -> occurrence.event().getTitle(), TITLE_ORDER));

		return occurrences;
	}

	public static List<Occurrence> expandEvent(Event event, Instant from, Instant to) {
		Instant start = event.getStartAt();
		Instant end = event.getEndAt();

		if (!isRecurring(event)) {
			return start.isBefore(to) && end.isAfter(from) ? List.of(new Occurrence(event)) : List.of();
		}

		Recurrence recurrence = event.getRecurrence();
		String timeZone = recurrence.getTimeZone() == null ? "UTC" : recurrence.getTimeZone();
		String anchorDate = TimeZones.localDateKey(start, timeZone);
		int anchorMinute = TimeZones.minuteOfDay(start, timeZone);
		Duration duration = Duration.between(start, end);
		Instant windowStart = from.minus(duration);
		String queryStartDate = TimeZones.localDateKey(start.isAfter(windowStart) ? start : windowStart, timeZone);
		String queryEndDate = TimeZones.localDateKey(to, timeZone);
		Integer countLimit = "count".equals(recurrence.getEndType()) ? recurrence.getCount() : null;
		String untilDate = "until".equals(recurrence.getEndType()) && recurrence.getUntil() != null
				? recurrenceUntilDate(recurrence.getUntil())
				: null;
		List<Occurrence> instances = new ArrayList<>();
		int matched = 0;
		String date = anchorDate;

		while (date.compareTo(queryEndDate) <= 0) {
			if (matchesDate(anchorDate, date, recurrence)) {
				matched += 1;

				if (countLimit != null && matched > countLimit) {
					break;
				}

				Instant occurrenceStart = TimeZones.zonedDateTime(date, anchorMinute, timeZone);

				if (untilDate != null && date.compareTo(untilDate) > 0) {
					break;
				}

				Instant occurrenceEnd = occurrenceStart.plus(duration);

				if (date.compareTo(queryStartDate) >= 0 && occurrenceStart.isBefore(to)
						&& occurrenceEnd.isAfter(from)) {
					instances.add(new Occurrence(event, occurrenceStart, occurrenceEnd, true, occurrenceStart));
				}
			}

			date = TimeZones.addCalendarDays(date, 1);
		}

		return instances;
	}

	public static ResponseState responseForOccurrence(Occurrence occurrence, String personId) {
		Event event = occurrence.event();
		ResponseState base = null;

		for (AttendeeResponse response : event.getAttendeeResponses()) {
			if (response.getPersonId().equals(personId)) {
				base = new ResponseState(response.getStatus(), response.getRespondedAt());
			}
		}

		if (occurrence.occurrenceStartAt() == null) {
			return base;
		}

		Instant occurrenceTime = occurrence.occurrenceStartAt();
		List<RecurrenceResponseOverride> overrides = event.getRecurrenceResponseOverrides().stream()
				.filter(override -> override.getPersonId().equals(personId)).toList();

		for (RecurrenceResponseOverride override : overrides) {
			if ("this".equals(override.getScope()) && override.getOccurrenceStartAt().equals(occurrenceTime)) {
				return new ResponseState(override.getStatus(), override.getRespondedAt());
			}
		}

		return overrides.stream()
				.filter(override -> "following".equals(override.getScope())
						&& !override.getOccurrenceStartAt().isAfter(occurrenceTime))
				.max(Comparator.comparing(RecurrenceResponseOverride::getOccurrenceStartAt))
				.map(override -> new ResponseState(override.getStatus(), override.getRespondedAt())).orElse(base);
	}

	private static boolean matchesDate(String anchorDate, String candidateDate, Recurrence recurrence) {
		long days = dayDifference(anchorDate, candidateDate);

		if (days < 0) {
			return false;
		}

		int interval = recurrence.getInterval() == 0 ? 1 : recurrence.getInterval();
		int weekday = TimeZones.dayOfWeek(candidateDate);
		String frequency = recurrence.getFrequency();

		if ("daily".equals(frequency)) {
			return days % interval == 0;
		}

		if ("weekdays".equals(frequency)) {
			return !TimeZones.isWeekend(candidateDate);
		}

		if ("weekly".equals(frequency)) {
			return matchesWeekly(anchorDate, days, weekday, recurrence, interval);
		}

		if ("monthly".equals(frequency)) {
			return matchesMonthly(anchorDate, candidateDate, weekday, recurrence, interval);
		}

		if ("yearly".equals(frequency)) {
			return matchesYearly(anchorDate, candidateDate, interval);
		}

		return false;
	}

	private static boolean matchesWeekly(String anchorDate, long days, int weekday, Recurrence recurrence,
			int interval) {
		long daysFromWeekStart = days + TimeZones.dayOfWeek(anchorDate) - weekday;
		long week = Math.round((double) daysFromWeekStart / DAYS_PER_WEEK);
		List<Integer> daysOfWeek = recurrence.getDaysOfWeek().isEmpty()
				? List.of(TimeZones.dayOfWeek(anchorDate))
				: recurrence.getDaysOfWeek();

		return week % interval == 0 && daysOfWeek.contains(weekday);
	}

	private static boolean matchesMonthly(String anchorDate, String candidateDate, int weekday, Recurrence recurrence,
			int interval) {
		long months = monthDifference(anchorDate, candidateDate);

		if (months < 0 || months % interval != 0) {
			return false;
		}

		if ("dayOfMonth".equals(recurrence.getMonthlyMode())) {
			return LocalDate.parse(anchorDate).getDayOfMonth() == LocalDate.parse(candidateDate).getDayOfMonth();
		}

		if (TimeZones.dayOfWeek(anchorDate) != weekday) {
			return false;
		}

		if (isLastWeekdayInMonth(anchorDate)) {
			return isLastWeekdayInMonth(candidateDate);
		}

		return ordinalInMonth(anchorDate) == ordinalInMonth(candidateDate);
	}

	private static boolean matchesYearly(String anchorDate, String candidateDate, int interval) {
		LocalDate anchor = LocalDate.parse(anchorDate);
		LocalDate candidate = LocalDate.parse(candidateDate);

		return candidate.getYear() >= anchor.getYear() && (candidate.getYear() - anchor.getYear()) % interval == 0
				&& candidate.getMonthValue() == anchor.getMonthValue()
				&& candidate.getDayOfMonth() == anchor.getDayOfMonth();
	}

	private static long dayDifference(String from, String to) {
		return ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to));
	}

	private static long monthDifference(String from, String to) {
		LocalDate first = LocalDate.parse(from);
		LocalDate second = LocalDate.parse(to);

		return (long) (second.getYear() - first.getYear()) * 12 + second.getMonthValue() - first.getMonthValue();
	}

	private static int ordinalInMonth(String dateKey) {
		return (LocalDate.parse(dateKey).getDayOfMonth() + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK;
	}

	private static boolean isLastWeekdayInMonth(String dateKey) {
		LocalDate date = LocalDate.parse(dateKey);

		return date.plusDays(DAYS_PER_WEEK).getMonthValue() != date.getMonthValue();
	}

	private Recurrences() {
	}
}
