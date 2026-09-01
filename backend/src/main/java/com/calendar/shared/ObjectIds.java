package com.calendar.shared;

import org.bson.types.ObjectId;

public final class ObjectIds {

	public static boolean isValid(String value) {
		return value != null && ObjectId.isValid(value);
	}

	private ObjectIds() {
	}
}
