package com.calendar.insights;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.calendar.shared.RequestContext;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {

	private final InsightService insightService;

	private final RequestContext requestContext;

	public InsightController(InsightService insightService, RequestContext requestContext) {
		this.insightService = insightService;
		this.requestContext = requestContext;
	}

	@GetMapping("/daily")
	public Map<String, Object> getDailyInsight(@RequestParam Map<String, String> parameters) {
		return Map.of("data",
				insightService.daily(InsightValidator.validateDaily(parameters), requestContext.profileId()));
	}
}
