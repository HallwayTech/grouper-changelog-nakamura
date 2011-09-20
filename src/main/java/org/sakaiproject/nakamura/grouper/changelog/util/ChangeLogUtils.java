package org.sakaiproject.nakamura.grouper.changelog.util;

import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;

public class ChangeLogUtils {
	
	/**
	 * Some events store the full grouper name on the name attribute, 
	 * others use grouperName.
	 * @param entry 
	 * @return the grouper name this entry references, null if not found or not supported.
	 */
	public static String getGrouperNameFromChangelogEntry(ChangeLogEntry entry){
		String grouperName = null;
		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)){
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_UPDATE)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.name);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_FIELD_ADD)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_FIELD_ADD.name);
		}
		return grouperName;
	}
}
