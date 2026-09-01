package com.calendar.events;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.calendar.calendars.Calendar;
import com.calendar.calendars.CalendarRepository;
import com.calendar.people.Person;
import com.calendar.people.PersonService;
import com.calendar.shared.ApiException;
import com.calendar.shared.Dates;
import com.calendar.shared.ObjectIds;
import com.calendar.shared.TimeZones;

@Service
public class EventService {

	private static final int BAD_REQUEST = 400;

	private static final int UNAUTHORIZED = 401;

	private static final int FORBIDDEN = 403;

	private static final int NOT_FOUND = 404;

	private static final int UNPROCESSABLE = 422;

	private static final int CONFLICT = 409;

	private static final String NOT_FOUND_CODE = "EVENT_NOT_FOUND";

	private static final String NOT_FOUND_MESSAGE = "The requested event does not exist.";

	private static final String NEEDS_ACTION = "needsAction";

	private static final List<String> RESPONSE_STATUSES = List.of(NEEDS_ACTION, "accepted", "declined", "tentative");

	private static final int SEARCH_HORIZON_DAYS = 366;

	private final EventRepository eventRepository;

	private final CalendarRepository calendarRepository;

	private final PersonService personService;

	public EventService(EventRepository eventRepository, CalendarRepository calendarRepository,
			PersonService personService) {
		this.eventRepository = eventRepository;
		this.calendarRepository = calendarRepository;
		this.personService = personService;
	}

	public List<Map<String, Object>> list(EventValidator.ListQuery query, String profileId) {
		List<Event> events = eventRepository.findInRange(query.from(), query.to(), query.calendarIds(), profileId);

		return decorate(Recurrences.expandEvents(events, query.from(), query.to()), profileId);
	}

	public List<Map<String, Object>> search(EventValidator.SearchQuery query, String profileId) {
		List<Event> events = eventRepository.search(query, profileId);

		if (query.from() == null && query.to() == null) {
			return decorate(events.stream().map(Occurrence::new).toList(), profileId);
		}

		Instant from = query.from() == null ? Instant.EPOCH : query.from();
		Instant to = query.to() == null ? Instant.now().plus(Duration.ofDays(SEARCH_HORIZON_DAYS)) : query.to();

		return decorate(Recurrences.expandEvents(events, from, to), profileId);
	}

	public Map<String, Object> getById(String id, String profileId) {
		ensureObjectId(id);

		Event event = eventRepository.findById(id);

		if (event == null || !canView(event, profileId)) {
			throw notFound();
		}

		return decorate(List.of(new Occurrence(event)), profileId).get(0);
	}

	public Map<String, Object> create(Map<String, Object> input, Person profile) {
		ensureCalendar((String) input.get("calendarId"), profile == null ? null : profile.getId());

		Event event = new Event();
		apply(event, input);

		if (profile != null) {
			event.setOrganizer(profile.getName());
		}

		normalizeParticipants(event);
		event.setAttendeeResponses(reconcileResponses(event.getParticipantIds(), List.of(), false));
		ensureChronology(event);
		ensureRecurrence(event);

		Event saved = eventRepository.save(event);

		return decorate(List.of(new Occurrence(saved)), profile == null ? null : profile.getId()).get(0);
	}

	public Map<String, Object> update(String id, Map<String, Object> input, String profileId) {
		ensureObjectId(id);

		Event existing = eventRepository.findById(id);

		if (existing == null || profileId != null && calendarRepository.findById(existing.getCalendarId(),
				profileId) == null) {
			throw notFound();
		}

		boolean scheduleChanged = hasScheduleChange(input, existing);
		List<AttendeeResponse> previousResponses = new ArrayList<>(existing.getAttendeeResponses());

		if (input.containsKey("calendarId")) {
			ensureCalendar((String) input.get("calendarId"), profileId);
		}

		apply(existing, input);

		if (input.containsKey("participants") || input.containsKey("participantIds")) {
			normalizeParticipants(existing);
		}

		if (scheduleChanged || input.containsKey("participantIds")) {
			existing.setAttendeeResponses(
					reconcileResponses(existing.getParticipantIds(), previousResponses, scheduleChanged));

			if (scheduleChanged) {
				existing.setRecurrenceResponseOverrides(new ArrayList<>());
			}
		}

		ensureChronology(existing);
		ensureRecurrence(existing);

		Event saved = eventRepository.save(existing);

		return decorate(List.of(new Occurrence(saved)), profileId).get(0);
	}

	public Map<String, Object> respond(String id, EventValidator.ResponseInput input, String profileId) {
		ensureObjectId(id);

		if (profileId == null) {
			throw new ApiException(UNAUTHORIZED, "PROFILE_REQUIRED",
					"Choose a profile before responding to an invitation.");
		}

		Event existing = eventRepository.findById(id);

		if (existing == null) {
			throw notFound();
		}

		if (!existing.getParticipantIds().contains(profileId)) {
			throw new ApiException(FORBIDDEN, "NOT_EVENT_ATTENDEE",
					"Only an invited attendee can respond to this event.");
		}

		boolean recurring = Recurrences.isRecurring(existing);

		if (!recurring && input.scope() != null && !"all".equals(input.scope())) {
			throw new ApiException(BAD_REQUEST, "NOT_RECURRING",
					"Occurrence scope is available only for recurring events.");
		}

		String scope = recurring && input.scope() != null ? input.scope() : "all";

		if (!"all".equals(scope) && input.occurrenceStartAt() == null) {
			throw new ApiException(BAD_REQUEST, "OCCURRENCE_REQUIRED",
					"Choose which occurrence should receive this response.");
		}

		Instant occurrenceStartAt = input.occurrenceStartAt();

		if (recurring && occurrenceStartAt != null) {
			ensureOccurrence(existing, occurrenceStartAt);
		}

		Event updated = applyResponse(existing, profileId, scope, occurrenceStartAt, input.status());

		if (updated == null) {
			throw new ApiException(CONFLICT, "INVITATION_CHANGED",
					"This invitation changed before your response was saved. Refresh and try again.");
		}

		return decorate(List.of(displayOccurrence(updated, recurring, occurrenceStartAt)), profileId).get(0);
	}

	public void remove(String id, String profileId) {
		ensureObjectId(id);

		Event existing = eventRepository.findById(id);

		if (existing == null || profileId != null && calendarRepository.findById(existing.getCalendarId(),
				profileId) == null) {
			throw notFound();
		}

		eventRepository.remove(id);
	}

	private Event applyResponse(Event event, String profileId, String scope, Instant occurrenceStartAt,
			String status) {
		Instant respondedAt = Instant.now();

		if ("all".equals(scope)) {
			boolean replaced = false;

			for (AttendeeResponse response : event.getAttendeeResponses()) {
				if (response.getPersonId().equals(profileId)) {
					response.setStatus(status);
					response.setRespondedAt(respondedAt);
					replaced = true;
				}
			}

			if (replaced) {
				event.getRecurrenceResponseOverrides()
						.removeIf(override -> override.getPersonId().equals(profileId));
			} else {
				event.getAttendeeResponses().add(new AttendeeResponse(profileId, status, respondedAt));
			}

			return eventRepository.save(event);
		}

		event.getRecurrenceResponseOverrides()
				.removeIf(override -> override.getPersonId().equals(profileId) && override.getScope().equals(scope)
						&& override.getOccurrenceStartAt().equals(occurrenceStartAt));
		event.getRecurrenceResponseOverrides()
				.add(new RecurrenceResponseOverride(profileId, occurrenceStartAt, scope, status, respondedAt));

		return eventRepository.save(event);
	}

	private Occurrence displayOccurrence(Event event, boolean recurring, Instant occurrenceStartAt) {
		if (!recurring || occurrenceStartAt == null) {
			return new Occurrence(event);
		}

		Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
		List<Occurrence> found = Recurrences.expandEvent(event, occurrenceStartAt.minusMillis(1),
				occurrenceStartAt.plus(duration).plusMillis(1));

		return found.isEmpty() ? new Occurrence(event) : found.get(0);
	}

	private void ensureOccurrence(Event event, Instant occurrenceStartAt) {
		Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
		boolean found = Recurrences
				.expandEvent(event, occurrenceStartAt.minusMillis(1), occurrenceStartAt.plus(duration).plusMillis(1))
				.stream().anyMatch(item -> item.occurrenceStartAt().equals(occurrenceStartAt));

		if (!found) {
			throw new ApiException(BAD_REQUEST, "INVALID_OCCURRENCE",
					"The selected occurrence does not belong to this recurring event.");
		}
	}

	@SuppressWarnings("unchecked")
	private void apply(Event event, Map<String, Object> input) {
		input.forEach((key, value) -> {
			switch (key) {
				case "calendarId" -> event.setCalendarId((String) value);
				case "title" -> event.setTitle((String) value);
				case "type" -> event.setType((String) value);
				case "description" -> event.setDescription((String) value);
				case "location" -> event.setLocation((String) value);
				case "organizer" -> event.setOrganizer((String) value);
				case "participants" -> event.setParticipants(new ArrayList<>((List<String>) value));
				case "participantIds" -> event.setParticipantIds(new ArrayList<>((List<String>) value));
				case "startAt" -> event.setStartAt((Instant) value);
				case "endAt" -> event.setEndAt((Instant) value);
				case "allDay" -> event.setAllDay((Boolean) value);
				case "color" -> event.setColor((String) value);
				case "recurrence" -> event.setRecurrence((Recurrence) value);
				default -> throw new IllegalStateException("Unsupported event field: " + key);
			}
		});
	}

	private void normalizeParticipants(Event event) {
		List<Person> selected = event.getParticipantIds().isEmpty() ? List.of()
				: personService.getSelected(event.getParticipantIds());
		Set<String> selectedNames = new LinkedHashSet<>();

		for (Person person : selected) {
			selectedNames.add(normalizedName(person.getName()));
		}

		List<String> names = new ArrayList<>(selected.stream().map(Person::getName).toList());

		for (String participant : event.getParticipants()) {
			String trimmed = participant.trim();

			if (!trimmed.isEmpty() && !selectedNames.contains(normalizedName(trimmed))) {
				names.add(trimmed);
			}
		}

		event.setParticipants(names);
	}

	private List<AttendeeResponse> reconcileResponses(List<String> participantIds,
			List<AttendeeResponse> previousResponses, boolean reset) {
		Map<String, AttendeeResponse> previousById = new LinkedHashMap<>();

		for (AttendeeResponse response : previousResponses) {
			previousById.put(response.getPersonId(), response);
		}

		List<AttendeeResponse> responses = new ArrayList<>();

		for (String personId : participantIds) {
			AttendeeResponse previous = previousById.get(personId);

			if (!reset && previous != null) {
				responses.add(new AttendeeResponse(personId, previous.getStatus(), previous.getRespondedAt()));
			} else {
				responses.add(new AttendeeResponse(personId, NEEDS_ACTION, null));
			}
		}

		return responses;
	}

	private boolean hasScheduleChange(Map<String, Object> input, Event existing) {
		if (input.containsKey("startAt") && !input.get("startAt").equals(existing.getStartAt())) {
			return true;
		}

		if (input.containsKey("endAt") && !input.get("endAt").equals(existing.getEndAt())) {
			return true;
		}

		if (input.containsKey("allDay") && !input.get("allDay").equals(existing.isAllDay())) {
			return true;
		}

		return input.containsKey("recurrence")
				&& !signature((Recurrence) input.get("recurrence")).equals(signature(existing.getRecurrence()));
	}

	private String signature(Recurrence recurrence) {
		List<Integer> days = new ArrayList<>(recurrence.getDaysOfWeek());
		days.sort(Integer::compareTo);

		return String.join("|", recurrence.getFrequency(), String.valueOf(recurrence.getInterval()),
				days.toString(), recurrence.getMonthlyMode(), recurrence.getEndType(),
				String.valueOf(recurrence.getCount()), String.valueOf(recurrence.getUntil()),
				recurrence.getTimeZone());
	}

	private boolean canView(Event event, String profileId) {
		return profileId == null || event.getParticipantIds().contains(profileId)
				|| calendarRepository.findById(event.getCalendarId(), profileId) != null;
	}

	private void ensureChronology(Event event) {
		if (!event.getStartAt().isBefore(event.getEndAt())) {
			throw new ApiException(BAD_REQUEST, "INVALID_EVENT_RANGE",
					"The event end time must be after its start time.");
		}
	}

	private void ensureRecurrence(Event event) {
		Recurrence recurrence = event.getRecurrence();

		if ("none".equals(recurrence.getFrequency()) || !"until".equals(recurrence.getEndType())
				|| recurrence.getUntil() == null) {
			return;
		}

		String timeZone = recurrence.getTimeZone() == null ? "UTC" : recurrence.getTimeZone();
		String startDate = TimeZones.localDateKey(event.getStartAt(), timeZone);

		if (Recurrences.recurrenceUntilDate(recurrence.getUntil()).compareTo(startDate) < 0) {
			throw new ApiException(BAD_REQUEST, "INVALID_RECURRENCE_END",
					"The recurrence end date cannot be before the event starts.");
		}
	}

	private void ensureCalendar(String calendarId, String profileId) {
		if (!ObjectIds.isValid(calendarId) || calendarRepository.findById(calendarId, profileId) == null) {
			throw new ApiException(UNPROCESSABLE, "INVALID_CALENDAR", "The selected calendar does not exist.");
		}
	}

	private void ensureObjectId(String id) {
		if (!ObjectIds.isValid(id)) {
			throw notFound();
		}
	}

	private ApiException notFound() {
		return new ApiException(NOT_FOUND, NOT_FOUND_CODE, NOT_FOUND_MESSAGE);
	}

	private String normalizedName(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	private List<Map<String, Object>> decorate(List<Occurrence> occurrences, String profileId) {
		List<String> calendarIds = occurrences.stream().map(occurrence -> occurrence.event().getCalendarId()).distinct()
				.toList();
		Map<String, String> ownerByCalendarId = new LinkedHashMap<>();

		for (Calendar calendar : calendarRepository.findOwners(calendarIds)) {
			ownerByCalendarId.put(calendar.getId(), calendar.getOwnerId());
		}

		Set<String> ids = new LinkedHashSet<>();

		for (Occurrence occurrence : occurrences) {
			ids.addAll(occurrence.event().getParticipantIds());
		}

		ownerByCalendarId.values().stream().filter(value -> value != null && !value.isEmpty()).forEach(ids::add);

		Map<String, Person> peopleById = new LinkedHashMap<>();

		for (Person person : ids.isEmpty() ? List.<Person>of() : personService.findExisting(new ArrayList<>(ids))) {
			peopleById.put(person.getId(), person);
		}

		Set<String> ownedIds = profileId == null ? null
				: calendarRepository.findOwnedIds(calendarIds, profileId).stream().map(Calendar::getId)
						.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

		return occurrences.stream()
				.map(occurrence -> describe(occurrence, profileId, ownerByCalendarId, peopleById, ownedIds)).toList();
	}

	private Map<String, Object> describe(Occurrence occurrence, String profileId,
			Map<String, String> ownerByCalendarId, Map<String, Person> peopleById, Set<String> ownedIds) {
		Event event = occurrence.event();
		Person host = peopleById.get(ownerByCalendarId.get(event.getCalendarId()));
		List<Person> directoryGuests = event.getParticipantIds().stream().map(peopleById::get)
				.filter(person -> person != null).toList();
		Set<String> directoryNames = new LinkedHashSet<>();

		for (Person person : directoryGuests) {
			directoryNames.add(normalizedName(person.getName()));
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("_id", event.getId());
		body.put("calendarId", event.getCalendarId());
		body.put("title", event.getTitle());
		body.put("type", event.getType());
		body.put("description", event.getDescription());
		body.put("location", event.getLocation());
		body.put("organizer", event.getOrganizer());
		body.put("participants", event.getParticipants());
		body.put("participantIds", event.getParticipantIds());
		body.put("attendeeResponses", event.getAttendeeResponses().stream().map(AttendeeResponse::toMap).toList());
		body.put("recurrence", event.getRecurrence().toMap());
		body.put("recurrenceResponseOverrides",
				event.getRecurrenceResponseOverrides().stream().map(RecurrenceResponseOverride::toMap).toList());
		body.put("startAt", Dates.iso(occurrence.startAt()));
		body.put("endAt", Dates.iso(occurrence.endAt()));
		body.put("allDay", event.isAllDay());

		if (event.getColor() != null) {
			body.put("color", event.getColor());
		}

		body.put("createdAt", Dates.iso(event.getCreatedAt()));
		body.put("updatedAt", Dates.iso(event.getUpdatedAt()));

		if (occurrence.recurring()) {
			body.put("seriesStartAt", Dates.iso(event.getStartAt()));
			body.put("seriesEndAt", Dates.iso(event.getEndAt()));
			body.put("recurring", true);
			body.put("occurrenceStartAt", Dates.iso(occurrence.occurrenceStartAt()));
			body.put("occurrenceKey", event.getId() + ":" + Dates.iso(occurrence.occurrenceStartAt()));
		}

		body.put("editable", ownedIds == null || ownedIds.contains(event.getCalendarId()));

		Recurrences.ResponseState current = profileId != null && event.getParticipantIds().contains(profileId)
				? currentResponse(occurrence, profileId)
				: null;

		if (current != null) {
			body.put("responseStatus", current.status());
		}

		body.put("respondedAt", current == null ? null : Dates.iso(current.respondedAt()));
		body.put("responseSummary", summarize(occurrence, directoryGuests));
		body.put("organizerPerson", host == null ? null : host.toSummaryMap());
		body.put("participantPeople", participantPeople(occurrence, directoryGuests, directoryNames));

		return body;
	}

	private Recurrences.ResponseState currentResponse(Occurrence occurrence, String profileId) {
		Recurrences.ResponseState found = Recurrences.responseForOccurrence(occurrence, profileId);

		return found == null ? new Recurrences.ResponseState(NEEDS_ACTION, null) : found;
	}

	private Map<String, Object> summarize(Occurrence occurrence, List<Person> directoryGuests) {
		Map<String, Object> summary = new LinkedHashMap<>();

		for (String status : RESPONSE_STATUSES) {
			summary.put(status, 0);
		}

		for (Person person : directoryGuests) {
			String status = statusFor(occurrence, person.getId());
			summary.put(status, (Integer) summary.get(status) + 1);
		}

		return summary;
	}

	private List<Map<String, Object>> participantPeople(Occurrence occurrence, List<Person> directoryGuests,
			Set<String> directoryNames) {
		List<Map<String, Object>> people = new ArrayList<>();

		for (Person person : directoryGuests) {
			Map<String, Object> entry = person.toSummaryMap();
			entry.put("responseStatus", statusFor(occurrence, person.getId()));
			people.add(entry);
		}

		int index = 0;

		for (String name : occurrence.event().getParticipants()) {
			if (directoryNames.contains(normalizedName(name))) {
				continue;
			}

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("_id", "saved-participant-" + occurrence.event().getId() + "-" + index);
			entry.put("name", name);
			entry.put("email", "");
			entry.put("avatarColor", "#5f6368");
			entry.put("responseStatus", NEEDS_ACTION);
			people.add(entry);
			index += 1;
		}

		return people;
	}

	private String statusFor(Occurrence occurrence, String personId) {
		Recurrences.ResponseState state = Recurrences.responseForOccurrence(occurrence, personId);

		return state == null ? NEEDS_ACTION : state.status();
	}
}
