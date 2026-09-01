package com.calendar.people;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.calendar.shared.RequestContext;

@RestController
public class PersonController {

	private final PersonService personService;

	private final RequestContext requestContext;

	public PersonController(PersonService personService, RequestContext requestContext) {
		this.personService = personService;
		this.requestContext = requestContext;
	}

	@GetMapping("/api/v1/people")
	public Map<String, Object> searchPeople(@RequestParam Map<String, String> parameters) {
		PersonValidator.Search search = PersonValidator.validateSearch(parameters);
		List<Person> people = personService.search(search.query(), search.limit(), requestContext.profileId());

		return Map.of("data", people.stream().map(Person::toMap).toList());
	}

	@GetMapping("/api/v1/profiles")
	public Map<String, Object> listProfiles() {
		List<Person> profiles = personService.listProfiles(requestContext.account().getAllowedProfileIds());

		return Map.of("data", profiles.stream().map(Person::toMap).toList());
	}
}
