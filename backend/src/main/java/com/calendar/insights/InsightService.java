package com.calendar.insights;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.calendar.calendars.Calendar;
import com.calendar.events.Event;
import com.calendar.events.Occurrence;
import com.calendar.events.Recurrences;
import com.calendar.shared.Dates;

@Service
public class InsightService {

	private static final int WORKING_DAY_MINUTES = 480;

	private static final int HISTORY_DAYS = 6;

	private static final int AVERAGE_DAYS = 7;

	private static final long MILLISECONDS_PER_MINUTE = 60000;

	private static final Set<String> TRACKED_TYPES = Set.of("event", "appointmentSchedule", "focusTime", "task",
			"outOfOffice");

	private record Category(String key, String label, String color) {
	}

	private static final List<Category> CATEGORIES = List.of(new Category("meetings", "Meetings", "#1a73e8"),
			new Category("focus", "Focus time", "#039be5"), new Category("tasks", "Tasks", "#7e57c2"),
			new Category("outOfOffice", "Out of office", "#f4511e"));

	private final InsightRepository insightRepository;

	public InsightService(InsightRepository insightRepository) {
		this.insightRepository = insightRepository;
	}

	public Map<String, Object> daily(InsightValidator.DailyQuery query, String profileId) {
		Instant historyFrom = query.from().minus(Duration.ofDays(HISTORY_DAYS));
		List<Event> stored = insightRepository.findEvents(historyFrom, query.to(), query.calendarIds(), profileId);
		List<Calendar> calendars = insightRepository.findCalendars(query.calendarIds(), profileId);
		List<Occurrence> events = Recurrences.expandEvents(stored, historyFrom, query.to());

		Map<String, Integer> categoryMinutes = new LinkedHashMap<>();
		CATEGORIES.forEach(category -> categoryMinutes.put(category.key(), 0));

		Map<String, Integer> calendarMinutes = new LinkedHashMap<>();
		int meetingCount = 0;

		for (Occurrence occurrence : events) {
			int minutes = clippedMinutes(occurrence, query.from(), query.to());
			String category = categoryFor(occurrence.event().getType());

			if (minutes == 0 || category == null) {
				continue;
			}

			categoryMinutes.merge(category, minutes, Integer::sum);

			if ("meetings".equals(category)) {
				meetingCount += 1;
			}

			calendarMinutes.merge(occurrence.event().getCalendarId(), minutes, Integer::sum);
		}

		int totalScheduledMinutes = categoryMinutes.values().stream().mapToInt(Integer::intValue).sum();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("date", Dates.iso(query.from()));
		body.put("workingDayMinutes", WORKING_DAY_MINUTES);
		body.put("totalScheduledMinutes", totalScheduledMinutes);
		body.put("meetingMinutes", categoryMinutes.get("meetings"));
		body.put("meetingCount", meetingCount);
		body.put("averageDailyMeetingMinutes", averageMeetingMinutes(events, historyFrom));
		body.put("remainingMinutes", Math.max(0, WORKING_DAY_MINUTES - Math.min(WORKING_DAY_MINUTES,
				totalScheduledMinutes)));
		body.put("categories", CATEGORIES.stream().map(category -> describe(category, categoryMinutes)).toList());
		body.put("calendars", describeCalendars(calendarMinutes, calendars));

		return body;
	}

	private Map<String, Object> describe(Category category, Map<String, Integer> categoryMinutes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("key", category.key());
		body.put("label", category.label());
		body.put("color", category.color());
		body.put("minutes", categoryMinutes.get(category.key()));

		return body;
	}

	private List<Map<String, Object>> describeCalendars(Map<String, Integer> calendarMinutes,
			List<Calendar> calendars) {
		Map<String, Calendar> lookup = new LinkedHashMap<>();

		for (Calendar calendar : calendars) {
			lookup.put(calendar.getId(), calendar);
		}

		List<Map<String, Object>> rows = new ArrayList<>();

		calendarMinutes.forEach((calendarId, minutes) -> {
			Calendar calendar = lookup.get(calendarId);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("calendarId", calendarId);
			row.put("name", calendar == null ? "Calendar" : calendar.getName());
			row.put("color", calendar == null ? "#5f6368" : calendar.getColor());
			row.put("minutes", minutes);
			rows.add(row);
		});

		rows.sort(Comparator.comparingInt((Map<String, Object> row) -> (Integer) row.get("minutes")).reversed()
				.thenComparing(row -> (String) row.get("name")));

		return rows;
	}

	private int averageMeetingMinutes(List<Occurrence> events, Instant historyFrom) {
		long total = 0;

		for (int index = 0; index < AVERAGE_DAYS; index += 1) {
			Instant dayStart = historyFrom.plus(Duration.ofDays(index));
			Instant dayEnd = dayStart.plus(Duration.ofDays(1));

			for (Occurrence occurrence : events) {
				if ("meetings".equals(categoryFor(occurrence.event().getType()))) {
					total += clippedMinutes(occurrence, dayStart, dayEnd);
				}
			}
		}

		return Math.round((float) total / AVERAGE_DAYS);
	}

	private int clippedMinutes(Occurrence occurrence, Instant from, Instant to) {
		Event event = occurrence.event();

		if (!TRACKED_TYPES.contains(event.getType())) {
			return 0;
		}

		if (event.isAllDay()) {
			return allDayMinutes(occurrence, from, to);
		}

		long start = Math.max(occurrence.startAt().toEpochMilli(), from.toEpochMilli());
		long end = Math.min(occurrence.endAt().toEpochMilli(), to.toEpochMilli());

		return (int) Math.max(0, Math.round((end - start) / (double) MILLISECONDS_PER_MINUTE));
	}

	private int allDayMinutes(Occurrence occurrence, Instant from, Instant to) {
		if (!"outOfOffice".equals(occurrence.event().getType())) {
			return 0;
		}

		long overlapStart = Math.max(occurrence.startAt().toEpochMilli(), from.toEpochMilli());
		long overlapEnd = Math.min(occurrence.endAt().toEpochMilli(), to.toEpochMilli());

		if (overlapEnd <= overlapStart) {
			return 0;
		}

		double coveredDays = Math.min(1, (overlapEnd - overlapStart) / (double) Duration.ofDays(1).toMillis());

		return (int) Math.round(WORKING_DAY_MINUTES * coveredDays);
	}

	private String categoryFor(String type) {
		return switch (type) {
			case "event", "appointmentSchedule" -> "meetings";
			case "focusTime" -> "focus";
			case "task" -> "tasks";
			case "outOfOffice" -> "outOfOffice";
			default -> null;
		};
	}
}
