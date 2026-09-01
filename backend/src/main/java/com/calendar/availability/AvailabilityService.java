package com.calendar.availability;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.calendar.events.Occurrence;
import com.calendar.events.Recurrences;
import com.calendar.people.BusyBlock;
import com.calendar.people.Person;
import com.calendar.people.PersonService;
import com.calendar.people.WorkingHours;
import com.calendar.shared.Dates;
import com.calendar.shared.TimeZones;

@Service
public class AvailabilityService {

	private static final int SUGGESTION_LIMIT = 12;

	private static final int SLOT_MINUTES = 30;

	private static final int MINUTES_PER_DAY = 1440;

	private final PersonService personService;

	private final AvailabilityRepository availabilityRepository;

	public AvailabilityService(PersonService personService, AvailabilityRepository availabilityRepository) {
		this.personService = personService;
		this.availabilityRepository = availabilityRepository;
	}

	public Map<String, Object> conflicts(AvailabilityValidator.ConflictInput input) {
		List<Person> people = personService.getSelected(input.participantIds());
		AvailabilityRepository.ParticipantEvents found = availabilityRepository
				.participantBusy(input.participantIds(), input.startAt(), input.endAt());
		List<Occurrence> scheduled = Recurrences.expandEvents(found.events(), input.startAt(), input.endAt());

		List<Map<String, Object>> conflicts = new ArrayList<>();
		List<Map<String, Object>> warnings = new ArrayList<>();

		for (Person person : people) {
			List<Map<String, Object>> busy = busyFor(person, scheduled, found.ownerByCalendarId(), input.startAt(),
					input.endAt());

			if (!busy.isEmpty()) {
				Map<String, Object> conflict = new LinkedHashMap<>();
				conflict.put("person", publicPerson(person, input.timeZone()));
				conflict.put("busy", busy);
				conflicts.add(conflict);
			}
		}

		for (Person person : people) {
			TimeZones.Status status = withinHours(input.startAt(), input.endAt(), person, input.timeZone());

			if (!status.withinWorkingHours()) {
				Map<String, Object> warning = new LinkedHashMap<>();
				warning.put("person", publicPerson(person, input.timeZone()));
				warning.put("withinWorkingHours", status.withinWorkingHours());
				warning.put("localDate", status.localDate());
				warning.put("localStartMinute", status.localStartMinute());
				warning.put("localEndMinute", status.localEndMinute());
				warning.put("workingDay", status.workingDay());
				warnings.add(warning);
			}
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startAt", Dates.iso(input.startAt()));
		body.put("endAt", Dates.iso(input.endAt()));
		body.put("available", conflicts.isEmpty());
		body.put("withinWorkingHours", warnings.isEmpty());
		body.put("conflicts", conflicts);
		body.put("workingHoursWarnings", warnings);

		return body;
	}

	public Map<String, Object> suggest(AvailabilityValidator.SuggestionInput input, Person profile) {
		List<Person> people = personService.getSelected(input.participantIds());
		Instant from = TimeZones.zonedDateTime(input.from(), 0, input.timeZone());
		Instant to = TimeZones.zonedDateTime(TimeZones.addCalendarDays(input.from(), input.days()), 0,
				input.timeZone());
		List<Occurrence> ownerBusy = Recurrences
				.expandEvents(availabilityRepository.ownerBusy(from, to, profile.getId()), from, to);
		AvailabilityRepository.ParticipantEvents found = availabilityRepository.participantBusy(input.participantIds(),
				from, to);
		List<Occurrence> scheduled = Recurrences.expandEvents(found.events(), from, to);

		List<Map<String, Object>> participants = new ArrayList<>();
		List<List<Map<String, Object>>> participantBusy = new ArrayList<>();

		for (Person person : people) {
			List<Map<String, Object>> busy = busyFor(person, scheduled, found.ownerByCalendarId(), from, to);
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("person", publicPerson(person, input.timeZone()));
			entry.put("workingIntervals", workingIntervals(input.from(), input.days(), input.timeZone(), person));
			entry.put("busy", busy);
			participants.add(entry);
			participantBusy.add(busy);
		}

		List<Map<String, Object>> ownerBlocks = clipOccurrences(ownerBusy, from, to);
		List<Map<String, Object>> suggestions = suggestions(input, profile, people, ownerBlocks, participantBusy);

		Map<String, Object> owner = new LinkedHashMap<>();
		owner.put("name", profile.getName());
		owner.put("timeZone", profile.getTimeZone());
		owner.put("workingHours", profile.getWorkingHours().toMap());
		owner.put("workingIntervals", workingIntervals(input.from(), input.days(), input.timeZone(), profile));
		owner.put("busy", ownerBlocks);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", Dates.iso(from));
		body.put("to", Dates.iso(to));
		body.put("durationMinutes", input.durationMinutes());
		body.put("timeZone", input.timeZone());
		body.put("owner", owner);
		body.put("participants", participants);
		body.put("suggestions", suggestions);

		return body;
	}

	private List<Map<String, Object>> suggestions(AvailabilityValidator.SuggestionInput input, Person profile,
			List<Person> people, List<Map<String, Object>> ownerBlocks,
			List<List<Map<String, Object>>> participantBusy) {
		List<Map<String, Object>> suggestions = new ArrayList<>();
		Instant now = Instant.now();

		for (int dayIndex = 0; dayIndex < input.days(); dayIndex += 1) {
			String date = TimeZones.addCalendarDays(input.from(), dayIndex);

			for (int minute = 0; minute + input.durationMinutes() <= MINUTES_PER_DAY; minute += SLOT_MINUTES) {
				Instant startAt = TimeZones.zonedDateTime(date, minute, input.timeZone());
				Instant endAt = startAt.plus(Duration.ofMinutes(input.durationMinutes()));

				if (!endAt.isAfter(now)) {
					continue;
				}

				if (!fitsWorkingHours(startAt, endAt, profile, people, input.timeZone())) {
					continue;
				}

				if (overlapsAny(startAt, endAt, ownerBlocks)
						|| participantBusy.stream().anyMatch(blocks -> overlapsAny(startAt, endAt, blocks))) {
					continue;
				}

				Map<String, Object> suggestion = new LinkedHashMap<>();
				suggestion.put("startAt", Dates.iso(startAt));
				suggestion.put("endAt", Dates.iso(endAt));
				suggestion.put("attendeeCount", people.size() + 1);
				suggestions.add(suggestion);

				if (suggestions.size() == SUGGESTION_LIMIT) {
					return suggestions;
				}
			}
		}

		return suggestions;
	}

	private boolean fitsWorkingHours(Instant startAt, Instant endAt, Person profile, List<Person> people,
			String timeZone) {
		if (!withinHours(startAt, endAt, profile, timeZone).withinWorkingHours()) {
			return false;
		}

		return people.stream().allMatch(person -> withinHours(startAt, endAt, person, timeZone).withinWorkingHours());
	}

	private boolean overlapsAny(Instant startAt, Instant endAt, List<Map<String, Object>> blocks) {
		return blocks.stream().anyMatch(block -> overlaps(startAt, endAt, (String) block.get("startAt"),
				(String) block.get("endAt")));
	}

	private boolean overlaps(Instant startAt, Instant endAt, String blockStart, String blockEnd) {
		return startAt.isBefore(Instant.parse(blockEnd)) && endAt.isAfter(Instant.parse(blockStart));
	}

	private List<Map<String, Object>> busyFor(Person person, List<Occurrence> scheduled,
			Map<String, String> ownerByCalendarId, Instant from, Instant to) {
		List<Map<String, Object>> blocks = new ArrayList<>(clipBusyBlocks(person.getBusyBlocks(), from, to));
		blocks.addAll(clipOccurrences(scheduledFor(scheduled, ownerByCalendarId, person.getId()), from, to));

		return blocks;
	}

	private List<Occurrence> scheduledFor(List<Occurrence> scheduled, Map<String, String> ownerByCalendarId,
			String personId) {
		List<Occurrence> owned = new ArrayList<>();

		for (Occurrence occurrence : scheduled) {
			String ownerId = ownerByCalendarId.get(occurrence.event().getCalendarId());

			if (personId.equals(ownerId)) {
				owned.add(occurrence);

				continue;
			}

			if (!occurrence.event().getParticipantIds().contains(personId)) {
				continue;
			}

			Recurrences.ResponseState state = Recurrences.responseForOccurrence(occurrence, personId);

			if (state == null || !"declined".equals(state.status())) {
				owned.add(occurrence);
			}
		}

		return owned;
	}

	private List<Map<String, Object>> clipBusyBlocks(List<BusyBlock> blocks, Instant from, Instant to) {
		List<Map<String, Object>> clipped = new ArrayList<>();

		for (BusyBlock block : blocks) {
			if (!overlapsWindow(from, to, block.getStartAt(), block.getEndAt())) {
				continue;
			}

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("title", block.getTitle() == null ? "Busy" : block.getTitle());
			entry.put("startAt", Dates.iso(later(from, block.getStartAt())));
			entry.put("endAt", Dates.iso(earlier(to, block.getEndAt())));
			clipped.add(entry);
		}

		return clipped;
	}

	private List<Map<String, Object>> clipOccurrences(List<Occurrence> occurrences, Instant from, Instant to) {
		List<Map<String, Object>> clipped = new ArrayList<>();

		for (Occurrence occurrence : occurrences) {
			if (!overlapsWindow(from, to, occurrence.startAt(), occurrence.endAt())) {
				continue;
			}

			clipped.add(describe(occurrence, from, to));
		}

		return clipped;
	}

	private Map<String, Object> describe(Occurrence occurrence, Instant from, Instant to) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("_id", occurrence.event().getId());
		entry.put("title", occurrence.event().getTitle());
		entry.put("calendarId", occurrence.event().getCalendarId());
		entry.put("type", occurrence.event().getType());
		putWhenPresent(entry, "description", occurrence.event().getDescription());
		putWhenPresent(entry, "location", occurrence.event().getLocation());
		putWhenPresent(entry, "organizer", occurrence.event().getOrganizer());

		if (!occurrence.event().getParticipants().isEmpty()) {
			entry.put("participants", occurrence.event().getParticipants());
		}

		putWhenPresent(entry, "color", occurrence.event().getColor());

		if (occurrence.event().isAllDay()) {
			entry.put("allDay", true);
		}

		entry.put("startAt", Dates.iso(later(from, occurrence.startAt())));
		entry.put("endAt", Dates.iso(earlier(to, occurrence.endAt())));

		return entry;
	}

	private void putWhenPresent(Map<String, Object> entry, String key, String value) {
		if (value != null && !value.isEmpty()) {
			entry.put(key, value);
		}
	}

	private List<Map<String, Object>> workingIntervals(String fromDate, int days, String displayTimeZone,
			Person person) {
		Instant from = TimeZones.zonedDateTime(fromDate, 0, displayTimeZone);
		Instant to = TimeZones.zonedDateTime(TimeZones.addCalendarDays(fromDate, days), 0, displayTimeZone);
		String zone = person.getTimeZone();
		WorkingHours hours = person.getWorkingHours();
		String firstLocalDate = TimeZones.localDateKey(from, zone);
		List<Map<String, Object>> intervals = new ArrayList<>();

		for (int offset = -1; offset <= days + 1; offset += 1) {
			String date = TimeZones.addCalendarDays(firstLocalDate, offset);

			if (TimeZones.isWeekend(date)) {
				continue;
			}

			Instant startAt = TimeZones.zonedDateTime(date, hours.getStartMinute(), zone);
			Instant endAt = TimeZones.zonedDateTime(date, hours.getEndMinute(), zone);

			if (!overlapsWindow(from, to, startAt, endAt)) {
				continue;
			}

			Map<String, Object> interval = new LinkedHashMap<>();
			interval.put("startAt", Dates.iso(later(from, startAt)));
			interval.put("endAt", Dates.iso(earlier(to, endAt)));
			intervals.add(interval);
		}

		return intervals;
	}

	private Map<String, Object> publicPerson(Person person, String fallbackTimeZone) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("_id", person.getId());
		body.put("name", person.getName());
		body.put("email", person.getEmail());
		body.put("avatarColor", person.getAvatarColor());
		body.put("workingHours", person.getWorkingHours().toMap());
		body.put("timeZone", person.getTimeZone() == null ? fallbackTimeZone : person.getTimeZone());

		return body;
	}

	private TimeZones.Status withinHours(Instant startAt, Instant endAt, Person person, String fallbackTimeZone) {
		WorkingHours hours = person.getWorkingHours();
		String zone = person.getTimeZone() == null ? fallbackTimeZone : person.getTimeZone();

		return TimeZones.workingHoursStatus(startAt, endAt, hours.getStartMinute(), hours.getEndMinute(), zone);
	}

	private boolean overlapsWindow(Instant from, Instant to, Instant startAt, Instant endAt) {
		return from.isBefore(endAt) && to.isAfter(startAt);
	}

	private Instant later(Instant first, Instant second) {
		return first.isAfter(second) ? first : second;
	}

	private Instant earlier(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
	}
}
