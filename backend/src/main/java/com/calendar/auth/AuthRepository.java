package com.calendar.auth;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {

	private final MongoTemplate mongoTemplate;

	public AuthRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public WorkspaceAccount findActiveByEmail(String email) {
		return mongoTemplate.findOne(Query.query(Criteria.where("email").is(email).and("active").is(true)),
				WorkspaceAccount.class);
	}

	public WorkspaceAccount findActiveById(String id) {
		return mongoTemplate.findOne(
				Query.query(Criteria.where("_id").is(new ObjectId(id)).and("active").is(true)), WorkspaceAccount.class);
	}
}
