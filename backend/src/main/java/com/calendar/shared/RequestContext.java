package com.calendar.shared;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.calendar.auth.WorkspaceAccount;
import com.calendar.people.Person;

@Component
public class RequestContext {

	public static final String ACCOUNT = "calendar.account";

	public static final String PROFILE = "calendar.profile";

	public WorkspaceAccount account() {
		return (WorkspaceAccount) attribute(ACCOUNT);
	}

	public Person profile() {
		return (Person) attribute(PROFILE);
	}

	public String profileId() {
		Person profile = profile();

		return profile == null ? null : profile.getId();
	}

	private Object attribute(String name) {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

		return attributes == null ? null : attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
	}
}
