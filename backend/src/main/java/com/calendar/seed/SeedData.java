package com.calendar.seed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.calendar.calendars.Calendar;
import com.calendar.events.AttendeeResponse;
import com.calendar.events.Event;
import com.calendar.events.Recurrence;
import com.calendar.events.RecurrenceResponseOverride;
import com.calendar.people.Person;
import com.calendar.people.WorkingHours;
import com.calendar.shared.TimeZones;

public class SeedData {

	public static final String DEMO_TIME_ZONE = "UTC";

	public static final String DEMO_PASSWORD = "password123";

	public static final int PASSWORD_ROUNDS = 12;

	public static final int SPREAD_FIRST_DAY = -45;

	public static final int SPREAD_LAST_DAY = 60;

	private static final int MINUTES_PER_HOUR = 60;

	private static final int MINUTES_PER_DAY = 1440;

	private static final int SLOT_MINUTES = 30;

	private static final int SLOT_CANDIDATE_COUNT = 24;

	private static final int DEFAULT_REPEAT_COUNT = 20;

	private static final int[] EVENTS_PER_DAY = { 3, 1, 2, 4, 2, 3, 1, 2 };

	private static final int TEMPLATE_DAY_STRIDE = 3;

	private static final int TEMPLATE_SLOT_STRIDE = 7;

	private static final String PRIMARY = "primary";

	private static final String WORK = "work";

	private static final String BIRTHDAYS = "birthdays";

	private static final String TYPE_EVENT = "event";

	private static final String TYPE_TASK = "task";

	private static final String TYPE_OUT_OF_OFFICE = "outOfOffice";

	private static final String TYPE_FOCUS_TIME = "focusTime";

	private static final String TYPE_WORKING_LOCATION = "workingLocation";

	private static final String TYPE_APPOINTMENT_SCHEDULE = "appointmentSchedule";

	private static final List<String> SUPPORTED_TYPES = List.of(TYPE_EVENT, TYPE_TASK, TYPE_OUT_OF_OFFICE,
			TYPE_FOCUS_TIME, TYPE_WORKING_LOCATION, TYPE_APPOINTMENT_SCHEDULE);

	private static final Map<String, Map<String, String>> DEMO_COLORS = Map.of(
			"alex.morgan@calendar.com", Map.of("profile", "#1a73e8", WORK, "#0b8043", BIRTHDAYS, "#c5221f"),
			"jordan.smith@calendar.com", Map.of("profile", "#b85c00", WORK, "#3f51b5"),
			"taylor.johnson@calendar.com", Map.of("profile", "#c2185b", WORK, "#00796b"),
			"riley.parker@calendar.com", Map.of("profile", "#00796b", WORK, "#795548"),
			"casey.bennett@calendar.com", Map.of("profile", "#7b1fa2", WORK, "#b85c00"));

	public record ProfileRow(String name, String email, int sortOrder, int startMinute, int endMinute) {
	}

	public static final List<ProfileRow> PROFILE_ROWS = List.of(
			new ProfileRow("Alex Morgan", "alex.morgan@calendar.com", 1, 540, 1050),
			new ProfileRow("Jordan Smith", "jordan.smith@calendar.com", 2, 540, 1020),
			new ProfileRow("Taylor Johnson", "taylor.johnson@calendar.com", 3, 600, 1080),
			new ProfileRow("Riley Parker", "riley.parker@calendar.com", 4, 480, 990),
			new ProfileRow("Casey Bennett", "casey.bennett@calendar.com", 5, 540, 1020));

	public record CalendarRow(String key, String ownerEmail, String name, String color, boolean isPrimary) {
	}

	private record SpreadTemplate(int owner, String calendar, String title, String type, String description,
			String location, int hour, int minutes, List<Integer> guests, List<String> statuses) {
	}

	private record DayOff(int owner, int offset, String description) {
	}

	private static final List<SpreadTemplate> SPREAD_TEMPLATES = List.of(
			new SpreadTemplate(0, WORK, "Design review", TYPE_EVENT,
					"Review the current product flow and resolve open decisions.", "Conference room Cedar", 10, 60,
					List.of(2, 4), List.of("accepted", "tentative")),
			new SpreadTemplate(0, PRIMARY, "Focus time", TYPE_FOCUS_TIME,
					"Protected time for the release-readiness write-up.", "", 14, 90, List.of(), List.of()),
			new SpreadTemplate(0, PRIMARY, "Publish weekly update", TYPE_TASK,
					"Summarize decisions, owners, and next steps.", "", 16, 30, List.of(), List.of()),
			new SpreadTemplate(1, WORK, "Project sync", TYPE_EVENT, "Review delivery status and clear blockers.",
					"Conference room Birch", 9, 45, List.of(3), List.of("accepted")),
			new SpreadTemplate(1, PRIMARY, "Customer discovery", TYPE_EVENT,
					"Understand the current scheduling workflow.", "Video call", 13, 60, List.of(4),
					List.of("accepted")),
			new SpreadTemplate(2, PRIMARY, "Research review", TYPE_EVENT, "Synthesize usability-study findings.", "",
					15, 60, List.of(0), List.of("needsAction")),
			new SpreadTemplate(2, PRIMARY, "Prototype exploration", TYPE_FOCUS_TIME, "", "", 11, 120, List.of(),
					List.of()),
			new SpreadTemplate(3, WORK, "Engineering review", TYPE_EVENT,
					"Review architecture, reliability, and rollout risks.", "Conference room Pine", 11, 60,
					List.of(1, 0), List.of("accepted", "tentative")),
			new SpreadTemplate(3, PRIMARY, "Incident drill", TYPE_EVENT, "Practice the service-recovery playbook.",
					"Operations room", 15, 60, List.of(), List.of()),
			new SpreadTemplate(4, WORK, "Campaign planning", TYPE_EVENT,
					"Coordinate launch messages and channel owners.", "Conference room Willow", 12, 60, List.of(0, 2),
					List.of("accepted", "accepted")),
			new SpreadTemplate(4, PRIMARY, "Editorial review", TYPE_EVENT, "Final review of the release announcement.",
					"", 16, 60, List.of(2), List.of("tentative")),
			new SpreadTemplate(0, PRIMARY, "Health appointment", TYPE_EVENT, "Annual preventive check-up.",
					"Community clinic", 17, 60, List.of(), List.of()),
			new SpreadTemplate(1, PRIMARY, "Code review", TYPE_FOCUS_TIME,
					"Review the authentication and recurrence changes.", "", 15, 90, List.of(), List.of()),
			new SpreadTemplate(2, PRIMARY, "Update accessibility notes", TYPE_TASK, "", "", 16, 30, List.of(),
					List.of()),
			new SpreadTemplate(3, PRIMARY, "Rotate service credentials", TYPE_TASK, "", "", 11, 30, List.of(),
					List.of()),
			new SpreadTemplate(4, PRIMARY, "Agency call", TYPE_EVENT, "Review the next creative delivery.",
					"Video call", 17, 45, List.of(), List.of()),
			new SpreadTemplate(0, WORK, "Team roadmap", TYPE_EVENT, "Align priorities for the next milestone.",
					"Orchid", 11, 60, List.of(1, 2), List.of("accepted", "tentative")),
			new SpreadTemplate(1, PRIMARY, "Deep work", TYPE_FOCUS_TIME, "", "", 10, 120, List.of(), List.of()),
			new SpreadTemplate(2, WORK, "Content workshop", TYPE_EVENT,
					"Improve empty, loading, and error-state language.", "Conference room Elm", 11, 90, List.of(0, 1),
					List.of("accepted", "declined")),
			new SpreadTemplate(3, WORK, "Release readiness", TYPE_EVENT,
					"Verify tests, monitoring, and rollback steps.", "", 10, 60, List.of(0, 1, 2),
					List.of("accepted", "accepted", "needsAction")),
			new SpreadTemplate(4, PRIMARY, "Write launch brief", TYPE_FOCUS_TIME, "", "", 10, 120, List.of(),
					List.of()),
			new SpreadTemplate(0, PRIMARY, "Read research notes", TYPE_TASK, "", "", 18, 30, List.of(), List.of()));

	private static final List<DayOff> DAYS_OFF = List.of(new DayOff(0, 21, "Unavailable for meetings."),
			new DayOff(1, 10, "Unavailable for the day."), new DayOff(2, 24, "Unavailable all day."),
			new DayOff(3, 31, "Unavailable for the day."), new DayOff(4, 16, "Unavailable for meetings."));

	private final String todayKey;

	private final List<Person> profiles;

	private final Map<String, Calendar> calendarsByKey;

	private final Instant respondedAt;

	private final List<Integer> workdayOffsets = new ArrayList<>();

	private final Map<String, List<int[]>> busy = new HashMap<>();

	public SeedData(String todayKey, List<Person> profiles, Map<String, Calendar> calendarsByKey) {
		this.todayKey = todayKey;
		this.profiles = profiles;
		this.calendarsByKey = calendarsByKey;
		this.respondedAt = date(-2, 12, 0);

		for (int offset = SPREAD_FIRST_DAY; offset <= SPREAD_LAST_DAY; offset += 1) {
			if (!isWeekend(offset)) {
				workdayOffsets.add(offset);
			}
		}
	}

	public static String colorFor(String email, String calendar) {
		Map<String, String> colors = DEMO_COLORS.get(email);

		return colors.getOrDefault(calendar, colors.get("profile"));
	}

	public static List<Person> profiles() {
		List<Person> people = new ArrayList<>();

		for (ProfileRow row : PROFILE_ROWS) {
			Person person = new Person();
			person.setName(row.name());
			person.setEmail(row.email());
			person.setAvatarColor(colorFor(row.email(), "profile"));
			person.setProfile(true);
			person.setSortOrder(row.sortOrder());
			person.setTimeZone(DEMO_TIME_ZONE);
			person.setWorkingHours(new WorkingHours(row.startMinute(), row.endMinute()));
			people.add(person);
		}

		return people;
	}

	public static List<CalendarRow> calendarRows() {
		List<CalendarRow> rows = new ArrayList<>();

		for (ProfileRow row : PROFILE_ROWS) {
			rows.add(new CalendarRow(row.email() + ":" + PRIMARY, row.email(), "My calendar",
					colorFor(row.email(), "profile"), true));
			rows.add(new CalendarRow(row.email() + ":" + WORK, row.email(), "Work", colorFor(row.email(), WORK),
					false));
		}

		String owner = PROFILE_ROWS.get(0).email();
		rows.add(new CalendarRow(owner + ":" + BIRTHDAYS, owner, "Birthdays", colorFor(owner, BIRTHDAYS), false));

		return rows;
	}

	public String dayKey(int dayOffset) {
		return TimeZones.addCalendarDays(todayKey, dayOffset);
	}

	public List<Event> buildEventRows() {
		List<Event> recurringRows = recurringRows();

		reserveRecurring(recurringRows);

		List<DayOff> daysOff = resolveDaysOff(recurringRows);

		declineOnDaysOff(recurringRows, daysOff);

		List<Event> milestoneRows = milestoneRows(daysOff);

		reserve(profiles.get(2).getName(), 4, 13 * MINUTES_PER_HOUR, 18 * MINUTES_PER_HOUR);

		List<Event> spreadRows = spreadRows();

		assertScheduleIsCoherent(recurringRows, spreadRows, daysOff);

		List<Event> rows = new ArrayList<>(recurringRows);
		rows.addAll(spreadRows);
		rows.addAll(milestoneRows);

		return rows;
	}

	public void validateEventRows(List<Event> rows, List<Calendar> calendars) {
		Map<String, Person> profileById = new HashMap<>();
		Map<String, String> calendarOwnerById = new HashMap<>();

		for (Person profile : profiles) {
			profileById.put(profile.getId(), profile);
		}

		for (Calendar calendar : calendars) {
			calendarOwnerById.put(calendar.getId(), calendar.getOwnerId());
		}

		for (Event row : rows) {
			String context = row.getOrganizer() + ": " + row.getTitle();

			if (row.getTitle() == null || row.getTitle().isBlank()) {
				throw new IllegalStateException("Seed event title is missing (" + context + ").");
			}

			if (!SUPPORTED_TYPES.contains(row.getType())) {
				throw new IllegalStateException("Seed event type is invalid (" + context + ").");
			}

			if (row.getStartAt() == null || row.getEndAt() == null || !row.getEndAt().isAfter(row.getStartAt())) {
				throw new IllegalStateException("Seed event range is invalid (" + context + ").");
			}

			Person owner = profileById.get(calendarOwnerById.get(row.getCalendarId()));

			if (owner == null || !owner.getName().equals(row.getOrganizer())) {
				throw new IllegalStateException("Seed calendar ownership is inconsistent (" + context + ").");
			}

			if (row.isAllDay() && !startsAtMidnight(row)) {
				throw new IllegalStateException(
						"Seed all-day boundaries must sit at midnight in " + DEMO_TIME_ZONE + " (" + context + ").");
			}

			List<String> participantIds = row.getParticipantIds();

			if (participantIds.stream().distinct().count() != participantIds.size()
					|| participantIds.contains(owner.getId())) {
				throw new IllegalStateException(
						"Seed participants are duplicated or include the organizer (" + context + ").");
			}

			List<String> expectedNames = participantIds.stream().map(id -> profileById.get(id).getName()).toList();

			if (!expectedNames.equals(row.getParticipants())) {
				throw new IllegalStateException(
						"Seed participant names and identifiers disagree (" + context + ").");
			}

			List<String> responseIds = row.getAttendeeResponses().stream().map(AttendeeResponse::getPersonId).toList();

			if (!responseIds.equals(participantIds)) {
				throw new IllegalStateException("Seed RSVP rows do not match the invitation list (" + context + ").");
			}

			Recurrence recurrence = row.getRecurrence();

			if (recurrence.getCount() != null && recurrence.getCount() < 1) {
				throw new IllegalStateException("Seed recurrence is invalid (" + context + ").");
			}
		}
	}

	private boolean startsAtMidnight(Event row) {
		return TimeZones.minuteOfDay(row.getStartAt(), DEMO_TIME_ZONE) == 0
				&& TimeZones.minuteOfDay(row.getEndAt(), DEMO_TIME_ZONE) == 0;
	}

	private List<Event> recurringRows() {
		Person alex = profiles.get(0);
		Person jordan = profiles.get(1);
		Person taylor = profiles.get(2);
		Person riley = profiles.get(3);
		Person casey = profiles.get(4);
		List<Event> rows = new ArrayList<>();

		Event planning = event(alex, WORK, "Weekly product planning", TYPE_EVENT,
				"Review priorities, risks, and the next delivery window.", "Conference room Maple", date(-7, 10, 0),
				date(-7, 11, 0));
		repeat(planning, "weekly", DEFAULT_REPEAT_COUNT, List.of());
		invite(planning, List.of(jordan, taylor, riley), List.of("accepted", "accepted", "tentative"));
		rows.add(planning);

		Event office = event(alex, PRIMARY, "Office", TYPE_WORKING_LOCATION, "Working from the office today.", "Office",
				date(-7, 0, 0), date(-6, 0, 0));
		office.setAllDay(true);
		repeat(office, "weekly", DEFAULT_REPEAT_COUNT, List.of(2, 4));
		rows.add(office);

		Event standUp = event(jordan, WORK, "Delivery stand-up", TYPE_EVENT, "Daily progress and blockers.", "",
				date(-8, 9, 15), date(-8, 9, 30));
		repeat(standUp, "weekdays", 60, List.of());
		invite(standUp, List.of(riley), List.of("accepted"));
		rows.add(standUp);

		Event home = event(jordan, PRIMARY, "Home", TYPE_WORKING_LOCATION, "Working remotely.", "Home", date(-9, 0, 0),
				date(-8, 0, 0));
		home.setAllDay(true);
		repeat(home, "weekly", DEFAULT_REPEAT_COUNT, List.of(1, 3));
		rows.add(home);

		Event critique = event(taylor, WORK, "Design critique", TYPE_EVENT,
				"Review the responsive Month and Week experiences.", "Studio", date(-6, 15, 0), date(-6, 16, 0));
		repeat(critique, "weekly", 14, List.of());
		invite(critique, List.of(casey), List.of("accepted"));
		rows.add(critique);

		Event oneOnOne = event(riley, PRIMARY, "One-on-one", TYPE_EVENT, "Weekly coaching conversation.", "",
				date(-5, 14, 0), date(-5, 14, 30));
		repeat(oneOnOne, "weekly", 16, List.of());
		invite(oneOnOne, List.of(jordan), List.of("accepted"));
		rows.add(oneOnOne);

		Event checkIn = event(casey, WORK, "Launch check-in", TYPE_EVENT, "Confirm launch dependencies and owners.", "",
				date(-8, 11, 0), date(-8, 11, 30));
		repeat(checkIn, "weekly", 14, List.of());
		invite(checkIn, List.of(alex, jordan), List.of("accepted", "accepted"));
		rows.add(checkIn);

		return rows;
	}

	private List<Event> milestoneRows(List<DayOff> daysOff) {
		Person alex = profiles.get(0);
		Person jordan = profiles.get(1);
		Person taylor = profiles.get(2);
		Person riley = profiles.get(3);
		Person casey = profiles.get(4);
		List<Event> rows = new ArrayList<>();

		Event birthday = event(alex, BIRTHDAYS, "Jordan’s birthday", TYPE_EVENT, "Birthday reminder.", "",
				date(3, 0, 0), date(4, 0, 0));
		birthday.setAllDay(true);
		rows.add(birthday);

		for (DayOff dayOff : daysOff) {
			Person owner = profiles.get(dayOff.owner());
			Event away = event(owner, PRIMARY, "Out of office", TYPE_OUT_OF_OFFICE, dayOff.description(), "",
					date(dayOff.offset(), 0, 0), date(dayOff.offset() + 1, 0, 0));
			away.setAllDay(true);
			rows.add(away);
		}

		rows.add(event(taylor, PRIMARY, "Out of office", TYPE_OUT_OF_OFFICE, "Unavailable after lunch.", "",
				date(4, 13, 0), date(4, 18, 0)));
		rows.add(event(alex, PRIMARY, "Office hours", TYPE_APPOINTMENT_SCHEDULE, "Open slots for project questions.",
				"Video call", date(6, 15, 0), date(6, 17, 0)));
		rows.add(event(jordan, PRIMARY, "Mentoring slots", TYPE_APPOINTMENT_SCHEDULE,
				"Book a 30-minute mentoring conversation.", "", date(12, 14, 0), date(12, 16, 0)));
		rows.add(event(taylor, PRIMARY, "Portfolio review slots", TYPE_APPOINTMENT_SCHEDULE,
				"Book a portfolio walkthrough.", "", date(18, 14, 0), date(18, 16, 0)));
		rows.add(event(riley, PRIMARY, "Technical office hours", TYPE_APPOINTMENT_SCHEDULE,
				"Open slots for architecture questions.", "", date(27, 15, 0), date(27, 17, 0)));
		rows.add(event(casey, PRIMARY, "Communications office hours", TYPE_APPOINTMENT_SCHEDULE,
				"Open slots for launch messaging.", "", date(9, 11, 0), date(9, 13, 0)));

		return rows;
	}

	private List<Event> spreadRows() {
		List<Event> rows = new ArrayList<>();

		for (int dayIndex = 0; dayIndex < workdayOffsets.size(); dayIndex += 1) {
			int offset = workdayOffsets.get(dayIndex);
			int perDay = EVENTS_PER_DAY[dayIndex % EVENTS_PER_DAY.length];

			for (int slot = 0; slot < perDay; slot += 1) {
				int index = (dayIndex * TEMPLATE_DAY_STRIDE + slot * TEMPLATE_SLOT_STRIDE) % SPREAD_TEMPLATES.size();
				SpreadTemplate template = SPREAD_TEMPLATES.get(index);
				Integer startMinute = findSlot(template, offset);

				if (startMinute == null) {
					continue;
				}

				rows.add(spreadRow(template, offset, startMinute));
			}
		}

		return rows;
	}

	private Event spreadRow(SpreadTemplate template, int offset, int startMinute) {
		Person owner = profiles.get(template.owner());
		List<Person> guests = template.guests().stream().map(profiles::get).toList();
		Instant startAt = date(offset, 0, startMinute);

		reserve(owner.getName(), offset, startMinute, startMinute + template.minutes());

		for (Person guest : guests) {
			reserve(guest.getName(), offset, startMinute, startMinute + template.minutes());
		}

		Event row = event(owner, template.calendar(), template.title(), template.type(), template.description(),
				template.location(), startAt, startAt.plusSeconds((long) template.minutes() * 60));

		if (!guests.isEmpty()) {
			invite(row, guests, template.statuses());
		}

		return row;
	}

	private Integer findSlot(SpreadTemplate template, int offset) {
		List<String> people = new ArrayList<>();
		people.add(profiles.get(template.owner()).getName());
		template.guests().forEach(guest -> people.add(profiles.get(guest).getName()));

		if (people.stream().anyMatch(name -> isAway(name, offset))) {
			return null;
		}

		int windowStart = people.stream().mapToInt(name -> hoursOf(name).getStartMinute()).max().orElseThrow();
		int windowEnd = people.stream().mapToInt(name -> hoursOf(name).getEndMinute()).min().orElseThrow();
		List<Integer> candidates = new ArrayList<>();
		candidates.add(template.hour() * MINUTES_PER_HOUR);

		for (int step = 0; step < SLOT_CANDIDATE_COUNT; step += 1) {
			candidates.add(windowStart + step * SLOT_MINUTES);
		}

		for (int startMinute : candidates) {
			int endMinute = startMinute + template.minutes();
			boolean fits = startMinute >= windowStart && endMinute <= windowEnd
					&& people.stream().allMatch(name -> isFree(name, offset, startMinute, endMinute));

			if (fits) {
				return startMinute;
			}
		}

		return null;
	}

	private List<DayOff> resolveDaysOff(List<Event> recurringRows) {
		List<DayOff> resolved = new ArrayList<>();

		for (DayOff candidate : DAYS_OFF) {
			String name = profiles.get(candidate.owner()).getName();
			Integer offset = workdayOffsets.stream().filter(day -> day >= candidate.offset())
					.filter(day -> !claimsALocation(recurringRows, name, day)).findFirst().orElse(null);

			if (offset == null) {
				throw new IllegalStateException("No clear out-of-office day for " + name + ".");
			}

			reserve(name, offset, 0, MINUTES_PER_DAY);
			resolved.add(new DayOff(candidate.owner(), offset, candidate.description()));
		}

		return resolved;
	}

	private void declineOnDaysOff(List<Event> recurringRows, List<DayOff> daysOff) {
		for (DayOff dayOff : daysOff) {
			Person owner = profiles.get(dayOff.owner());

			for (Event row : recurringRows) {
				boolean isGuest = row.getParticipantIds().contains(owner.getId());

				if (!isGuest || !repeatCoversDay(row, dayOff.offset())) {
					continue;
				}

				row.getRecurrenceResponseOverrides()
						.add(new RecurrenceResponseOverride(owner.getId(),
								date(dayOff.offset(), 0, TimeZones.minuteOfDay(row.getStartAt(), DEMO_TIME_ZONE)),
								"this", "declined", respondedAt));
			}
		}
	}

	private void reserveRecurring(List<Event> recurringRows) {
		for (Event row : recurringRows) {
			if (row.isAllDay()) {
				continue;
			}

			int startMinute = TimeZones.minuteOfDay(row.getStartAt(), DEMO_TIME_ZONE);
			int endMinute = startMinute
					+ (int) ((row.getEndAt().toEpochMilli() - row.getStartAt().toEpochMilli()) / 60000);

			for (int offset : workdayOffsets) {
				if (!repeatCoversDay(row, offset)) {
					continue;
				}

				reserve(row.getOrganizer(), offset, startMinute, endMinute);

				for (String name : row.getParticipants()) {
					reserve(name, offset, startMinute, endMinute);
				}
			}
		}
	}

	private boolean claimsALocation(List<Event> recurringRows, String name, int offset) {
		return recurringRows.stream().anyMatch(row -> TYPE_WORKING_LOCATION.equals(row.getType())
				&& row.getOrganizer().equals(name) && repeatCoversDay(row, offset));
	}

	private boolean repeatCoversDay(Event row, int offset) {
		String anchorKey = TimeZones.localDateKey(row.getStartAt(), DEMO_TIME_ZONE);
		String key = dayKey(offset);

		if (key.compareTo(anchorKey) < 0) {
			return false;
		}

		if (key.equals(anchorKey)) {
			return true;
		}

		String frequency = row.getRecurrence().getFrequency();

		if ("weekdays".equals(frequency)) {
			return !isWeekend(offset);
		}

		if ("weekly".equals(frequency)) {
			return row.getRecurrence().getDaysOfWeek().contains(TimeZones.dayOfWeek(key));
		}

		return false;
	}

	private void assertScheduleIsCoherent(List<Event> recurringRows, List<Event> spreadRows, List<DayOff> daysOff) {
		Map<String, Integer> daysOffByName = new LinkedHashMap<>();

		for (DayOff dayOff : daysOff) {
			daysOffByName.put(profiles.get(dayOff.owner()).getName(), dayOff.offset());
		}

		for (Map.Entry<String, List<int[]>> entry : busy.entrySet()) {
			List<int[]> timed = entry.getValue().stream().filter(slot -> slot[1] - slot[0] < MINUTES_PER_DAY).toList();

			for (int first = 0; first < timed.size(); first += 1) {
				for (int second = first + 1; second < timed.size(); second += 1) {
					if (timed.get(first)[0] >= timed.get(second)[1] || timed.get(first)[1] <= timed.get(second)[0]) {
						continue;
					}

					fail(entry.getKey().replace("|", " is double booked on day "));
				}
			}
		}

		for (Event row : spreadRows) {
			Integer offset = offsetOfRow(row);
			int startMinute = TimeZones.minuteOfDay(row.getStartAt(), DEMO_TIME_ZONE);
			int endMinute = startMinute
					+ (int) ((row.getEndAt().toEpochMilli() - row.getStartAt().toEpochMilli()) / 60000);

			if (offset == null || isWeekend(offset)) {
				fail("\"" + row.getTitle() + "\" does not land on a workday");
			}

			List<String> names = new ArrayList<>();
			names.add(row.getOrganizer());
			names.addAll(row.getParticipants());

			for (String name : names) {
				WorkingHours hours = hoursOf(name);

				if (startMinute < hours.getStartMinute() || endMinute > hours.getEndMinute()) {
					fail("\"" + row.getTitle() + "\" falls outside " + name + "'s working hours");
				}

				if (offset.equals(daysOffByName.get(name))) {
					fail(name + " has \"" + row.getTitle() + "\" on their day off");
				}
			}
		}

		for (Map.Entry<String, Integer> entry : daysOffByName.entrySet()) {
			if (claimsALocation(recurringRows, entry.getKey(), entry.getValue())) {
				fail(entry.getKey() + " claims a working location on their day off");
			}
		}

		for (int offset : workdayOffsets) {
			for (Person profile : profiles) {
				long locations = recurringRows.stream().filter(row -> TYPE_WORKING_LOCATION.equals(row.getType())
						&& row.getOrganizer().equals(profile.getName()) && repeatCoversDay(row, offset)).count();

				if (locations > 1) {
					fail(profile.getName() + " claims " + locations + " working locations on day " + offset);
				}
			}
		}
	}

	private Integer offsetOfRow(Event row) {
		String key = TimeZones.localDateKey(row.getStartAt(), DEMO_TIME_ZONE);

		return workdayOffsets.stream().filter(offset -> dayKey(offset).equals(key)).findFirst().orElse(null);
	}

	private void fail(String reason) {
		throw new IllegalStateException("Seed schedule is incoherent: " + reason + ".");
	}

	private Event event(Person owner, String calendarName, String title, String type, String description,
			String location, Instant startAt, Instant endAt) {
		Event row = new Event();
		row.setCalendarId(calendarsByKey.get(owner.getEmail() + ":" + calendarName).getId());
		row.setOrganizer(owner.getName());
		row.setTitle(title);
		row.setType(type);
		row.setDescription(description);
		row.setLocation(location);
		row.setStartAt(startAt);
		row.setEndAt(endAt);

		return row;
	}

	private void repeat(Event row, String frequency, int count, List<Integer> daysOfWeek) {
		Recurrence recurrence = new Recurrence();
		recurrence.setFrequency(frequency);
		recurrence.setEndType("count");
		recurrence.setCount(count);
		recurrence.setTimeZone(DEMO_TIME_ZONE);
		recurrence.setDaysOfWeek(new ArrayList<>(daysOfWeek));

		if ("weekly".equals(frequency) && daysOfWeek.isEmpty()) {
			recurrence.setDaysOfWeek(new ArrayList<>(
					List.of(TimeZones.dayOfWeek(TimeZones.localDateKey(row.getStartAt(), DEMO_TIME_ZONE)))));
		}

		row.setRecurrence(recurrence);
	}

	private void invite(Event row, List<Person> people, List<String> statuses) {
		List<String> names = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		List<AttendeeResponse> responses = new ArrayList<>();

		for (int index = 0; index < people.size(); index += 1) {
			Person person = people.get(index);
			String status = index < statuses.size() ? statuses.get(index) : "needsAction";
			names.add(person.getName());
			ids.add(person.getId());
			responses.add(new AttendeeResponse(person.getId(), status,
					"needsAction".equals(status) ? null : respondedAt));
		}

		row.setParticipants(names);
		row.setParticipantIds(ids);
		row.setAttendeeResponses(responses);
	}

	private WorkingHours hoursOf(String name) {
		return profiles.stream().filter(profile -> profile.getName().equals(name)).findFirst().orElseThrow()
				.getWorkingHours();
	}

	private Instant date(int dayOffset, int hour, int minute) {
		return TimeZones.zonedDateTime(dayKey(dayOffset), hour * MINUTES_PER_HOUR + minute, DEMO_TIME_ZONE);
	}

	private boolean isWeekend(int dayOffset) {
		return TimeZones.isWeekend(dayKey(dayOffset));
	}

	private List<int[]> slotsFor(String name, int offset) {
		return busy.getOrDefault(name + "|" + offset, List.of());
	}

	private void reserve(String name, int offset, int startMinute, int endMinute) {
		busy.computeIfAbsent(name + "|" + offset, key -> new ArrayList<>())
				.add(new int[] { startMinute, endMinute });
	}

	private boolean isFree(String name, int offset, int startMinute, int endMinute) {
		return slotsFor(name, offset).stream().noneMatch(slot -> startMinute < slot[1] && endMinute > slot[0]);
	}

	private boolean isAway(String name, int offset) {
		return slotsFor(name, offset).stream().anyMatch(slot -> slot[1] - slot[0] >= MINUTES_PER_DAY);
	}
}
