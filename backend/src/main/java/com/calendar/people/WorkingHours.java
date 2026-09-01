package com.calendar.people;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorkingHours {

	private static final int DEFAULT_START_MINUTE = 540;

	private static final int DEFAULT_END_MINUTE = 1020;

	private int startMinute = DEFAULT_START_MINUTE;

	private int endMinute = DEFAULT_END_MINUTE;

	public WorkingHours() {
	}

	public WorkingHours(int startMinute, int endMinute) {
		this.startMinute = startMinute;
		this.endMinute = endMinute;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startMinute", startMinute);
		body.put("endMinute", endMinute);

		return body;
	}

	public int getStartMinute() {
		return startMinute;
	}

	public void setStartMinute(int startMinute) {
		this.startMinute = startMinute;
	}

	public int getEndMinute() {
		return endMinute;
	}

	public void setEndMinute(int endMinute) {
		this.endMinute = endMinute;
	}
}
