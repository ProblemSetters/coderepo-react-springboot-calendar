package com.calendar.calendars;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.calendar.events.EventRepository;
import com.calendar.shared.ApiException;
import com.calendar.shared.ObjectIds;

@Service
public class CalendarService {

	private static final int NOT_FOUND = 404;

	private static final int CONFLICT = 409;

	private static final String NOT_FOUND_CODE = "CALENDAR_NOT_FOUND";

	private static final String NOT_FOUND_MESSAGE = "The requested calendar does not exist.";

	private static final String CONFLICT_CODE = "CALENDAR_NAME_CONFLICT";

	private static final String CONFLICT_MESSAGE = "A calendar with this name already exists.";

	private final CalendarRepository calendarRepository;

	private final EventRepository eventRepository;

	public CalendarService(CalendarRepository calendarRepository, EventRepository eventRepository) {
		this.calendarRepository = calendarRepository;
		this.eventRepository = eventRepository;
	}

	public List<Calendar> list(String ownerId) {
		return calendarRepository.list(ownerId);
	}

	public List<Calendar> displayOnly(String id, String ownerId) {
		requireCalendar(id, ownerId);

		return calendarRepository.displayOnly(id, ownerId);
	}

	public Calendar create(CalendarValidator.CreateInput input, String ownerId) {
		ensureUniqueName(input.name(), null, ownerId);

		Calendar calendar = new Calendar();
		calendar.setOwnerId(ownerId);
		calendar.setName(input.name());
		calendar.setColor(input.color());
		calendar.setDefaultColor(input.color());
		calendar.setDescription(input.description());
		calendar.setTimeZone(input.timeZone());
		calendar.setVisible(true);
		calendar.setPrimary(false);

		return calendarRepository.create(calendar);
	}

	public Calendar update(String id, Map<String, Object> input, String ownerId) {
		ensureId(id);

		if (input.containsKey("name")) {
			ensureUniqueName((String) input.get("name"), id, ownerId);
		}

		requireCalendar(id, ownerId);

		Calendar calendar = calendarRepository.update(id, new LinkedHashMap<>(input));

		if (calendar == null) {
			throw notFound();
		}

		return calendar;
	}

	public void remove(String id, String ownerId) {
		Calendar calendar = requireCalendar(id, ownerId);

		if (calendar.isPrimary()) {
			throw new ApiException(CONFLICT, "PRIMARY_CALENDAR", "The primary calendar cannot be deleted.");
		}

		long eventCount = eventRepository.countByCalendarId(id);

		if (eventCount > 0) {
			throw new ApiException(CONFLICT, "CALENDAR_NOT_EMPTY",
					"Move or delete this calendar's events before deleting it.", Map.of("eventCount", eventCount));
		}

		calendarRepository.remove(id, ownerId);
	}

	private Calendar requireCalendar(String id, String ownerId) {
		ensureId(id);

		Calendar calendar = calendarRepository.findById(id, ownerId);

		if (calendar == null) {
			throw notFound();
		}

		return calendar;
	}

	private void ensureId(String id) {
		if (!ObjectIds.isValid(id)) {
			throw notFound();
		}
	}

	private void ensureUniqueName(String name, String excludedId, String ownerId) {
		if (name != null && calendarRepository.findByName(name, excludedId, ownerId) != null) {
			throw new ApiException(CONFLICT, CONFLICT_CODE, CONFLICT_MESSAGE);
		}
	}

	private ApiException notFound() {
		return new ApiException(NOT_FOUND, NOT_FOUND_CODE, NOT_FOUND_MESSAGE);
	}
}
