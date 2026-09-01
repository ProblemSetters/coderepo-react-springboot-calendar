package com.calendar.calendars;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.calendar.shared.RequestContext;

@RestController
@RequestMapping("/api/v1/calendars")
public class CalendarController {

	private final CalendarService calendarService;

	private final RequestContext requestContext;

	public CalendarController(CalendarService calendarService, RequestContext requestContext) {
		this.calendarService = calendarService;
		this.requestContext = requestContext;
	}

	@GetMapping
	public Map<String, Object> listCalendars() {
		return Map.of("data",
				calendarService.list(requestContext.profileId()).stream().map(Calendar::toMap).toList());
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> createCalendar(@RequestBody(required = false) Map<String, Object> body) {
		Calendar calendar = calendarService.create(CalendarValidator.validateCreate(body), requestContext.profileId());

		return ResponseEntity.status(201).body(Map.of("data", calendar.toMap()));
	}

	@PostMapping("/{calendarId}/display-only")
	public Map<String, Object> displayOnlyCalendar(@PathVariable String calendarId) {
		return Map.of("data", calendarService.displayOnly(calendarId, requestContext.profileId()).stream()
				.map(Calendar::toMap).toList());
	}

	@PatchMapping("/{calendarId}")
	public Map<String, Object> updateCalendar(@PathVariable String calendarId,
			@RequestBody(required = false) Map<String, Object> body) {
		Calendar calendar = calendarService.update(calendarId, CalendarValidator.validateUpdate(body),
				requestContext.profileId());

		return Map.of("data", calendar.toMap());
	}

	@DeleteMapping("/{calendarId}")
	public ResponseEntity<Void> deleteCalendar(@PathVariable String calendarId) {
		calendarService.remove(calendarId, requestContext.profileId());

		return ResponseEntity.noContent().build();
	}
}
