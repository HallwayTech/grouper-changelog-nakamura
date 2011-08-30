package org.sakaiproject.nakamura.grouper.changelog.exceptions;

public class UserModificationException extends Exception {
	private static final long serialVersionUID = -6959893265128284169L;

	public int code = -1;

	public UserModificationException() {
		super();
	}

	public UserModificationException(String message) {
		super(message);
	}

	public UserModificationException(int code, String message) {
		super(message);
		this.code = code;
	}
}
