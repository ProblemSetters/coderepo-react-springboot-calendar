package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.calendar.events.Event;
import com.calendar.events.Occurrence;
import com.calendar.events.Recurrence;
import com.calendar.events.Recurrences;

class RecurrenceTest {

	private static Event repeating(String startAt, String endAt, Recurrence recurrence) {
		Event event = new Event();
		event.setId("series");
		event.setTitle("Series");
		event.setStartAt(Instant.parse(startAt));
		event.setEndAt(Instant.parse(endAt));
		event.setRecurrence(recurrence);

		return event;
	}

	private static Recurrence recurrence(String frequency, String endType, String timeZone) {
		Recurrence recurrence = new Recurrence();
		recurrence.setFrequency(frequency);
		recurrence.setInterval(1);
		recurrence.setDaysOfWeek(List.of());
		recurrence.setEndType(endType);
		recurrence.setTimeZone(timeZone);

		return recurrence;
	}

	private static List<Instant> startsOf(Event event, String from, String to) {
		return Recurrences.expandEvent(event, Instant.parse(from), Instant.parse(to)).stream()
				.map(Occurrence::startAt).toList();
	}

	private static List<Instant> instants(String... values) {
		return Arrays.stream(values).map(Instant::parse).toList();
	}

	@Test
	@DisplayName("a weekly repeat on chosen weekdays stops at its end date")
	void aWeeklyRepeatOnChosenWeekdaysStopsAtItsEndDate() {
		Recurrence rule = recurrence("weekly", "until", "UTC");
		rule.setDaysOfWeek(List.of(1, 3));
		rule.setUntil(Instant.parse("2030-06-12T00:00:00.000Z"));
		Event series = repeating("2030-06-03T10:00:00.000Z", "2030-06-03T10:30:00.000Z", rule);

		assertThat(startsOf(series, "2030-06-01T00:00:00.000Z", "2030-07-01T00:00:00.000Z"))
				.isEqualTo(instants("2030-06-03T10:00:00.000Z", "2030-06-05T10:00:00.000Z", "2030-06-10T10:00:00.000Z",
						"2030-06-12T10:00:00.000Z"));
	}

	@Test
	@DisplayName("a monthly repeat on the first Monday stops after its count")
	void aMonthlyRepeatOnTheFirstMondayStopsAfterItsCount() {
		Recurrence rule = recurrence("monthly", "count", "UTC");
		rule.setMonthlyMode("ordinalWeekday");
		rule.setCount(3);
		Event series = repeating("2030-06-03T09:00:00.000Z", "2030-06-03T10:00:00.000Z", rule);

		assertThat(startsOf(series, "2030-06-01T00:00:00.000Z", "2031-01-01T00:00:00.000Z"))
				.isEqualTo(instants("2030-06-03T09:00:00.000Z", "2030-07-01T09:00:00.000Z", "2030-08-05T09:00:00.000Z"));
	}

	@Test
	@DisplayName("a repeat keeps its local time across a daylight saving change")
	void aRepeatKeepsItsLocalTimeAcrossADaylightSavingChange() {
		Recurrence rule = recurrence("weekly", "never", "America/New_York");
		Event series = repeating("2030-03-04T14:00:00.000Z", "2030-03-04T15:00:00.000Z", rule);

		assertThat(startsOf(series, "2030-03-01T00:00:00.000Z", "2030-03-15T00:00:00.000Z"))
				.isEqualTo(instants("2030-03-04T14:00:00.000Z", "2030-03-11T13:00:00.000Z"));
	}

	@Test
	@DisplayName("a yearly repeat lands on the same date each year and an every-other-day repeat skips a day")
	void aYearlyRepeatLandsOnTheSameDateEachYearAndAnEveryOtherDayRepeatSkipsADay() {
		Event yearly = repeating("2030-06-03T10:00:00.000Z", "2030-06-03T11:00:00.000Z",
				recurrence("yearly", "never", "UTC"));
		Recurrence everyOther = recurrence("daily", "never", "UTC");
		everyOther.setInterval(2);
		Event everyOtherDay = repeating("2030-06-03T10:00:00.000Z", "2030-06-03T10:30:00.000Z", everyOther);

		assertThat(startsOf(yearly, "2030-01-01T00:00:00.000Z", "2033-01-01T00:00:00.000Z"))
				.isEqualTo(instants("2030-06-03T10:00:00.000Z", "2031-06-03T10:00:00.000Z", "2032-06-03T10:00:00.000Z"));
		assertThat(startsOf(everyOtherDay, "2030-06-01T00:00:00.000Z", "2030-06-09T00:00:00.000Z"))
				.isEqualTo(instants("2030-06-03T10:00:00.000Z", "2030-06-05T10:00:00.000Z", "2030-06-07T10:00:00.000Z"));
	}
}
