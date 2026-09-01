package com.calendar.insights;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.calendar.calendars.Calendar;
import com.calendar.events.Event;
import com.calendar.events.Recurrences;

@Repository
public class InsightRepository {

	private final MongoTemplate mongoTemplate;

	public InsightRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public List<Event> findEvents(Instant from, Instant to, List<String> calendarIds, String ownerId) {
		List<String> scoped = scopedCalendarIds(calendarIds, ownerId);
		List<Criteria> conditions = new ArrayList<>();
		conditions.add(overlapping(from, to));

		if (ownerId != null || !scoped.isEmpty()) {
			conditions.add(Criteria.where("calendarId").in(objectIds(scoped)));
		}

		Query query = Query.query(new Criteria().andOperator(conditions.toArray(new Criteria[0])));
		query.with(Sort.by(Sort.Order.asc("startAt")));

		return mongoTemplate.find(query, Event.class);
	}

	public List<Calendar> findCalendars(List<String> calendarIds, String ownerId) {
		List<String> scoped = scopedCalendarIds(calendarIds, ownerId);

		if (ownerId == null && scoped.isEmpty()) {
			return mongoTemplate.findAll(Calendar.class);
		}

		return mongoTemplate.find(Query.query(Criteria.where("_id").in(objectIds(scoped))), Calendar.class);
	}

	private List<String> scopedCalendarIds(List<String> calendarIds, String ownerId) {
		if (ownerId == null) {
			return calendarIds;
		}

		Query query = Query.query(Criteria.where("ownerId").is(new ObjectId(ownerId)));
		query.fields().include("_id");
		List<String> owned = mongoTemplate.find(query, Calendar.class).stream().map(Calendar::getId).toList();

		if (calendarIds.isEmpty()) {
			return owned;
		}

		return calendarIds.stream().filter(owned::contains).toList();
	}

	private Criteria overlapping(Instant from, Instant to) {
		Criteria plain = Criteria.where("startAt").lt(to).and("endAt").gt(from);
		Criteria repeating = new Criteria().andOperator(
				Criteria.where("recurrence.frequency").in(Recurrences.repeatingFrequencies()),
				Criteria.where("startAt").lt(to),
				new Criteria().orOperator(Criteria.where("recurrence.endType").ne("until"),
						Criteria.where("recurrence.until").gte(from)));

		return new Criteria().orOperator(plain, repeating);
	}

	private List<ObjectId> objectIds(List<String> ids) {
		return ids.stream().map(ObjectId::new).toList();
	}
}
