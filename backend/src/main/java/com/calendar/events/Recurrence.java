package com.calendar.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

import com.calendar.shared.Dates;

public class Recurrence {

	private String frequency = "none";

	private int interval = 1;

	private List<Integer> daysOfWeek = new ArrayList<>();

	private String monthlyMode = "ordinalWeekday";

	private String endType = "never";

	@Field(write = Field.Write.ALWAYS)
	private Integer count;

	@Field(write = Field.Write.ALWAYS)
	private Instant until;

	private String timeZone = "UTC";

	public Map<String, Object> toMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("frequency", frequency);
		body.put("interval", interval);
		body.put("daysOfWeek", daysOfWeek);
		body.put("monthlyMode", monthlyMode);
		body.put("endType", endType);
		body.put("count", count);
		body.put("until", Dates.iso(until));
		body.put("timeZone", timeZone);

		return body;
	}

	public String getFrequency() {
		return frequency;
	}

	public void setFrequency(String frequency) {
		this.frequency = frequency;
	}

	public int getInterval() {
		return interval;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}

	public List<Integer> getDaysOfWeek() {
		return daysOfWeek;
	}

	public void setDaysOfWeek(List<Integer> daysOfWeek) {
		this.daysOfWeek = daysOfWeek;
	}

	public String getMonthlyMode() {
		return monthlyMode;
	}

	public void setMonthlyMode(String monthlyMode) {
		this.monthlyMode = monthlyMode;
	}

	public String getEndType() {
		return endType;
	}

	public void setEndType(String endType) {
		this.endType = endType;
	}

	public Integer getCount() {
		return count;
	}

	public void setCount(Integer count) {
		this.count = count;
	}

	public Instant getUntil() {
		return until;
	}

	public void setUntil(Instant until) {
		this.until = until;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}
}
