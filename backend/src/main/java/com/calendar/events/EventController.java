package com.calendar.events;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.calendar.shared.RequestContext;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

	private final EventService eventService;

	private final RequestContext requestContext;

	public EventController(EventService eventService, RequestContext requestContext) {
		this.eventService = eventService;
		this.requestContext = requestContext;
	}

	@GetMapping
	public Map<String, Object> listEvents(@RequestParam Map<String, String> parameters) {
		return Map.of("data",
				eventService.list(EventValidator.validateList(parameters), requestContext.profileId()));
	}

	@GetMapping("/search")
	public Map<String, Object> searchEvents(@RequestParam Map<String, String> parameters) {
		return Map.of("data",
				eventService.search(EventValidator.validateSearch(parameters), requestContext.profileId()));
	}

	@GetMapping("/{eventId}")
	public Map<String, Object> getEvent(@PathVariable String eventId) {
		return Map.of("data", eventService.getById(eventId, requestContext.profileId()));
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> createEvent(@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> event = eventService.create(EventValidator.validateCreate(body), requestContext.profile());

		return ResponseEntity.status(201).body(Map.of("data", event));
	}

	@PatchMapping("/{eventId}/response")
	public Map<String, Object> respondToEvent(@PathVariable String eventId,
			@RequestBody(required = false) Map<String, Object> body) {
		EventValidator.ResponseInput input = EventValidator.validateResponse(body);

		return Map.of("data", eventService.respond(eventId, input, requestContext.profileId()));
	}

	@PatchMapping("/{eventId}")
	public Map<String, Object> updateEvent(@PathVariable String eventId,
			@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> input = EventValidator.validateUpdate(body);

		return Map.of("data", eventService.update(eventId, input, requestContext.profileId()));
	}

	@DeleteMapping("/{eventId}")
	public ResponseEntity<Void> deleteEvent(@PathVariable String eventId) {
		eventService.remove(eventId, requestContext.profileId());

		return ResponseEntity.noContent().build();
	}
}
