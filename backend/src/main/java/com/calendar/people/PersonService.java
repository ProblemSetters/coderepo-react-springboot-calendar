package com.calendar.people;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.calendar.shared.ApiException;
import com.calendar.shared.ObjectIds;

@Service
public class PersonService {

	private static final int NOT_FOUND = 404;

	private static final int BAD_REQUEST = 400;

	private final PersonRepository personRepository;

	public PersonService(PersonRepository personRepository) {
		this.personRepository = personRepository;
	}

	public List<Person> search(String query, int limit, String excludedId) {
		return personRepository.search(query, limit, excludedId);
	}

	public List<Person> listProfiles(List<String> ids) {
		return personRepository.listProfiles(ids);
	}

	public Person findProfileById(String id) {
		return personRepository.findProfileById(id);
	}

	public List<Person> findExisting(List<String> ids) {
		return personRepository.findByIds(ids);
	}

	public List<Person> getSelected(List<String> ids) {
		if (ids.stream().anyMatch(id -> !ObjectIds.isValid(id))) {
			throw new ApiException(BAD_REQUEST, "INVALID_PERSON_ID", "Every participant identifier must be valid.");
		}

		List<Person> people = personRepository.findByIds(ids);

		if (people.size() != ids.size()) {
			throw new ApiException(NOT_FOUND, "PEOPLE_NOT_FOUND", "One or more selected people no longer exist.");
		}

		Map<String, Person> byId = new LinkedHashMap<>();

		for (Person person : people) {
			byId.put(person.getId(), person);
		}

		return ids.stream().map(byId::get).toList();
	}
}
