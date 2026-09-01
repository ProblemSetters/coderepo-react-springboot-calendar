package com.calendar.people;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class PersonRepository {

	private final MongoTemplate mongoTemplate;

	public PersonRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public List<Person> search(String query, int limit, String excludedId) {
		Query search = new Query();

		if (!query.isEmpty()) {
			Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
			search.addCriteria(new Criteria().orOperator(Criteria.where("name").regex(pattern),
					Criteria.where("email").regex(pattern)));
		}

		if (excludedId != null) {
			search.addCriteria(Criteria.where("_id").ne(new org.bson.types.ObjectId(excludedId)));
		}

		search.fields().exclude("busyBlocks");
		search.with(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("email"))).limit(limit);

		return mongoTemplate.find(search, Person.class);
	}

	public List<Person> findByIds(List<String> ids) {
		return mongoTemplate.find(Query.query(Criteria.where("_id").in(objectIds(ids))), Person.class);
	}

	public Person findProfileById(String id) {
		Query query = Query.query(Criteria.where("_id").is(new org.bson.types.ObjectId(id)).and("isProfile").is(true));
		query.fields().exclude("busyBlocks");

		return mongoTemplate.findOne(query, Person.class);
	}

	public List<Person> listProfiles(List<String> ids) {
		Criteria criteria = Criteria.where("isProfile").is(true);

		if (ids != null) {
			criteria = criteria.and("_id").in(objectIds(ids));
		}

		Query query = Query.query(criteria);
		query.fields().exclude("busyBlocks");
		query.with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name")));

		return mongoTemplate.find(query, Person.class);
	}

	private List<org.bson.types.ObjectId> objectIds(List<String> ids) {
		return ids.stream().map(org.bson.types.ObjectId::new).toList();
	}
}
