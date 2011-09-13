package org.sakaiproject.nakamura.grouper.changelog.log;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class AuditLogUtils {
	public static final String AUDIT_LOGGER_NAME = "org.sakaiproject.nakamura.grouper.changelog.log.audit";

	private static final Log log = LogFactory.getLog(AUDIT_LOGGER_NAME);

	public static final String ERROR = "ERROR";

	public static final String USER_CREATED = "UC";

	// Membership
	public static final String USER_ADDED = "UA";
	public static final String USER_DELETED = "UD";

	// Groups
	public static final String GROUP_CREATED = "GC";
	public static final String GROUP_MODIFIED = "GM";
	public static final String GROUP_DELETED = "GD";

	public static final int SUCCESS = 1;
	public static final int NOACTION = 0;
	public static final int FAILURE = -1;

	private static final String DELIMETER = ",";

	/**
	 * Create an entry in the audit log
 	 * @param action the action the provisioning consumers took
	 * @param user for user creation and membership actions this is the target user
	 * @param group for group and membership actions this is the target group
	 * @param description A free form field for extra information about the action
	 * @param statusFlag indicates the result of the operation
	 */
	public static void audit(String action, String user, String group, String description, int statusFlag){
		if (action == null){
			action = ERROR;
		}
		if (user == null){
			user = "N/A";
		}
		if (group == null){
			group = "N/A";
		}
		if (description == null){
			description = "N/A";
		}
		StringBuilder sb = new StringBuilder(action);
		sb.append(DELIMETER);
		sb.append(user);
		sb.append(DELIMETER);
		sb.append(group);
		sb.append(DELIMETER);
		sb.append(description);
		sb.append(DELIMETER);
		sb.append(statusFlag);

		log.info(sb.toString());
	}
}