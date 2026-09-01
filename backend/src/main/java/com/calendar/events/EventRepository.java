package com.calendar.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

	private static final int SEARCH_LIMIT = 100;

	private final MongoTemplate mongoTemplate;

	public EventRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public List<Event> findInRange(Instant from, Instant to, List<String> calendarIds, String profileId) {
		List<Criteria> conditions = new ArrayList<>();
		conditions.add(overlapping(from, to));

		Criteria scope = scope(calendarIds, profileId);

		if (scope != null) {
			conditions.add(scope);
		}

		return mongoTemplate.find(ordered(Query.query(all(conditions))), Event.class);
	}

	public List<Event> search(EventValidator.SearchQuery query, String profileId) {
		List<Criteria> conditions = new ArrayList<>();
		Criteria scope = scope(query.calendarIds(), profileId);

		if (scope != null) {
			conditions.add(scope);
		}

		if (!query.what().isEmpty()) {
			conditions.add(anyOf(query.what(), List.of("title", "description", "location", "participants")));
		}

		if (query.who() != null) {
			conditions.add(anyOf(query.who(), List.of("organizer", "participants")));
		}

		if (query.where() != null) {
			conditions.add(Criteria.where("location").regex(expression(query.where())));
		}

		if (query.exclude() != null) {
			conditions.add(new Criteria().norOperator(
					fieldMatches(query.exclude(), List.of("title", "description", "location", "organizer",
							"participants"))));
		}

		if (query.from() != null || query.to() != null) {
			conditions.add(searchWindow(query.from(), query.to()));
		}

		return mongoTemplate.find(ordered(Query.query(all(conditions))).limit(SEARCH_LIMIT), Event.class);
	}

	public Event findById(String id) {
		return mongoTemplate.findById(new ObjectId(id), Event.class);
	}

	public long countByCalendarId(String calendarId) {
		return mongoTemplate.count(Query.query(Criteria.where("calendarId").is(new ObjectId(calendarId))), Event.class);
	}

	public Event save(Event event) {
		return mongoTemplate.save(event);
	}

	public void remove(String id) {
		mongoTemplate.remove(Query.query(Criteria.where("_id").is(new ObjectId(id))), Event.class);
	}

	private Criteria scope(List<String> calendarIds, String profileId) {
		if (profileId != null) {
			List<Criteria> options = new ArrayList<>();

			if (!calendarIds.isEmpty()) {
				options.add(Criteria.where("calendarId").in(objectIds(calendarIds)));
			}

			options.add(Criteria.where("participantIds").is(new ObjectId(profileId)));

			return new Criteria().orOperator(options.toArray(new Criteria[0]));
		}

		return calendarIds.isEmpty() ? null : Criteria.where("calendarId").in(objectIds(calendarIds));
	}

	private Criteria overlapping(Instant from, Instant to) {
		Criteria plain = Criteria.where("startAt").lt(to).and("endAt").gt(from);
		Criteria repeating = new Criteria().andOperator(
				Criteria.where("recurrence.frequency").in(Recurrences.repeatingFrequencies()),
				Criteria.where("startAt").lt(to), notEndedBefore(from));

		return new Criteria().orOperator(plain, repeating);
	}

	private Criteria searchWindow(Instant from, Instant to) {
		List<Criteria> plain = new ArrayList<>();
		List<Criteria> repeating = new ArrayList<>();
		repeating.add(Criteria.where("recurrence.frequency").in(Recurrences.repeatingFrequencies()));

		if (from != null) {
			plain.add(Criteria.where("endAt").gt(from));
			repeating.add(notEndedBefore(from));
		}

		if (to != null) {
			plain.add(Criteria.where("startAt").lt(to));
			repeating.add(Criteria.where("startAt").lt(to));
		}

		return new Criteria().orOperator(all(plain), all(repeating));
	}

	private Criteria notEndedBefore(Instant from) {
		return new Criteria().orOperator(Criteria.where("recurrence.endType").ne("until"),
				Criteria.where("recurrence.until").gte(from));
	}

	private Criteria anyOf(String value, List<String> fields) {
		return new Criteria().orOperator(fieldMatches(value, fields));
	}

	private Criteria[] fieldMatches(String value, List<String> fields) {
		return fields.stream().map(field -> Criteria.where(field).regex(expression(value))).toArray(Criteria[]::new);
	}

	private Pattern expression(String value) {
		return Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE);
	}

	private Criteria all(List<Criteria> conditions) {
		return conditions.isEmpty() ? new Criteria() : new Criteria().andOperator(conditions.toArray(new Criteria[0]));
	}

	private Query ordered(Query query) {
		return query.with(Sort.by(Sort.Order.asc("startAt"), Sort.Order.asc("title")));
	}

	private List<ObjectId> objectIds(List<String> ids) {
		return ids.stream().map(ObjectId::new).toList();
	}
}
