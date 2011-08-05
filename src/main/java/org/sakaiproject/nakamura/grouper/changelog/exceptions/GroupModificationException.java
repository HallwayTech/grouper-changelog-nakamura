package org.sakaiproject.nakamura.grouper.changelog.exceptions;

public class GroupModificationException extends Exception {
	private static final long serialVersionUID = -6959893265128284169L;

	public int code = -1;

	public GroupModificationException() {
		super();
	}

	public GroupModificationException(String message) {
		super(message);
	}

	public GroupModificationException(int code, String message) {
		super(message);
		this.code = code;
	}
}
