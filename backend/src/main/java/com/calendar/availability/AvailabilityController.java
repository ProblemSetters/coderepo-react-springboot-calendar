package com.calendar.availability;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.calendar.shared.RequestContext;

@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

	private final AvailabilityService availabilityService;

	private final RequestContext requestContext;

	public AvailabilityController(AvailabilityService availabilityService, RequestContext requestContext) {
		this.availabilityService = availabilityService;
		this.requestContext = requestContext;
	}

	@PostMapping("/suggestions")
	public Map<String, Object> suggestTimes(@RequestBody(required = false) Map<String, Object> body) {
		AvailabilityValidator.SuggestionInput input = AvailabilityValidator.validateSuggestions(body);

		return Map.of("data", availabilityService.suggest(input, requestContext.profile()));
	}

	@PostMapping("/conflicts")
	public Map<String, Object> checkConflicts(@RequestBody(required = false) Map<String, Object> body) {
		AvailabilityValidator.ConflictInput input = AvailabilityValidator.validateConflicts(body);

		return Map.of("data", availabilityService.conflicts(input));
	}
}
