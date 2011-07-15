package org.sakaiproject.nakamura.grouper.changelog.exceptions;

public class GroupAlreadyExistsException extends GroupModificationException {
	public GroupAlreadyExistsException(String message) {
		super(message);
	}

	private static final long serialVersionUID = 2791997975511251280L;
}
