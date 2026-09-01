package com.calendar.calendars;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class CalendarRepository {

	private final MongoTemplate mongoTemplate;

	public CalendarRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public List<Calendar> list(String ownerId) {
		return mongoTemplate.find(ordered(scoped(ownerId)), Calendar.class);
	}

	public Calendar findById(String id, String ownerId) {
		Criteria criteria = Criteria.where("_id").is(new ObjectId(id));

		if (ownerId != null) {
			criteria = criteria.and("ownerId").is(new ObjectId(ownerId));
		}

		return mongoTemplate.findOne(Query.query(criteria), Calendar.class);
	}

	public List<Calendar> findOwnedIds(List<String> ids, String ownerId) {
		Query query = Query.query(Criteria.where("ownerId").is(new ObjectId(ownerId)).and("_id").in(objectIds(ids)));
		query.fields().include("_id");

		return mongoTemplate.find(query, Calendar.class);
	}

	public List<Calendar> findOwners(List<String> ids) {
		Query query = Query.query(Criteria.where("_id").in(objectIds(ids)));
		query.fields().include("_id").include("ownerId");

		return mongoTemplate.find(query, Calendar.class);
	}

	public Calendar findByName(String name, String excludedId, String ownerId) {
		Criteria criteria = Criteria.where("name")
				.regex(Pattern.compile("^" + Pattern.quote(name) + "$", Pattern.CASE_INSENSITIVE));

		if (ownerId != null) {
			criteria = criteria.and("ownerId").is(new ObjectId(ownerId));
		}

		if (excludedId != null) {
			criteria = criteria.and("_id").ne(new ObjectId(excludedId));
		}

		return mongoTemplate.findOne(Query.query(criteria), Calendar.class);
	}

	public Calendar create(Calendar calendar) {
		return mongoTemplate.insert(calendar);
	}

	public Calendar update(String id, Map<String, Object> fields) {
		Update update = new Update();
		fields.forEach(update::set);
		update.set("updatedAt", Instant.now());
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id))), update, Calendar.class);

		return findById(id, null);
	}

	public List<Calendar> displayOnly(String id, String ownerId) {
		Criteria others = scopedCriteria(ownerId).and("_id").ne(new ObjectId(id)).and("visible").is(true);
		mongoTemplate.updateMulti(Query.query(others), visibility(false), Calendar.class);

		Criteria target = scopedCriteria(ownerId).and("_id").is(new ObjectId(id));
		mongoTemplate.updateFirst(Query.query(target), visibility(true), Calendar.class);

		return mongoTemplate.find(ordered(scoped(ownerId)), Calendar.class);
	}

	public void remove(String id, String ownerId) {
		Criteria criteria = Criteria.where("_id").is(new ObjectId(id));

		if (ownerId != null) {
			criteria = criteria.and("ownerId").is(new ObjectId(ownerId));
		}

		mongoTemplate.remove(Query.query(criteria), Calendar.class);
	}

	private Update visibility(boolean visible) {
		return new Update().set("visible", visible).set("updatedAt", Instant.now());
	}

	private Criteria scopedCriteria(String ownerId) {
		return ownerId == null ? new Criteria() : Criteria.where("ownerId").is(new ObjectId(ownerId));
	}

	private Query scoped(String ownerId) {
		return ownerId == null ? new Query()
				: Query.query(Criteria.where("ownerId").is(new ObjectId(ownerId)));
	}

	private Query ordered(Query query) {
		return query.with(Sort.by(Sort.Order.desc("isPrimary"), Sort.Order.asc("name")));
	}

	private List<ObjectId> objectIds(List<String> ids) {
		return ids.stream().map(ObjectId::new).toList();
	}
}
