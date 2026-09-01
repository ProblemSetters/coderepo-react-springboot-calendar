package com.calendar.availability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
public class AvailabilityRepository {

	public record ParticipantEvents(List<Event> events, Map<String, String> ownerByCalendarId) {
	}

	private final MongoTemplate mongoTemplate;

	public AvailabilityRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public List<Event> ownerBusy(Instant from, Instant to, String profileId) {
		List<Criteria> conditions = new ArrayList<>();
		conditions.add(overlapping(from, to));
		conditions.add(Criteria.where("type").ne("workingLocation"));
		conditions.add(new Criteria().orOperator(Criteria.where("allDay").is(false),
				Criteria.where("type").is("outOfOffice")));

		if (profileId != null) {
			conditions.add(Criteria.where("calendarId").in(calendarIdsOwnedBy(List.of(profileId))));
		}

		Query query = Query.query(new Criteria().andOperator(conditions.toArray(new Criteria[0])));
		query.with(Sort.by(Sort.Order.asc("startAt")));

		return mongoTemplate.find(query, Event.class);
	}

	public ParticipantEvents participantBusy(List<String> participantIds, Instant from, Instant to) {
		Query calendarQuery = Query.query(Criteria.where("ownerId").in(objectIds(participantIds)));
		calendarQuery.fields().include("_id").include("ownerId");
		List<Calendar> calendars = mongoTemplate.find(calendarQuery, Calendar.class);
		Map<String, String> ownerByCalendarId = new LinkedHashMap<>();
		List<ObjectId> calendarIds = new ArrayList<>();

		for (Calendar calendar : calendars) {
			ownerByCalendarId.put(calendar.getId(), calendar.getOwnerId());
			calendarIds.add(new ObjectId(calendar.getId()));
		}

		Criteria audience = new Criteria().orOperator(
				Criteria.where("participantIds").in(objectIds(participantIds)),
				Criteria.where("calendarId").in(calendarIds));
		Criteria consuming = new Criteria().orOperator(Criteria.where("allDay").is(false),
				Criteria.where("type").is("outOfOffice"));
		Query query = Query.query(new Criteria().andOperator(audience, consuming, overlapping(from, to),
				Criteria.where("type").ne("workingLocation")));
		query.with(Sort.by(Sort.Order.asc("startAt")));

		return new ParticipantEvents(mongoTemplate.find(query, Event.class), ownerByCalendarId);
	}

	private List<ObjectId> calendarIdsOwnedBy(List<String> ownerIds) {
		Query query = Query.query(Criteria.where("ownerId").in(objectIds(ownerIds)));
		query.fields().include("_id");

		return mongoTemplate.find(query, Calendar.class).stream().map(calendar -> new ObjectId(calendar.getId()))
				.toList();
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
