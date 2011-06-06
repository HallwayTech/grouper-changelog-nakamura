package org.sakaiproject.nakamura.grouper.changelog.util;

import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

public class LastStemGroupIdAdapter extends BaseNakamuraGroupIdAdapter implements GroupIdAdapter {
	
	protected static final String[] PSEUDO_GROUP_SUFFIXES = { "-manager", "-student", "-ta", "-lecturer", "-member" };

	public LastStemGroupIdAdapter(String basestem){
		super(basestem);
	}

	/**
	 * base:stem:some_group:members => some_group
	 * base:stem:some_group:managers => some_group-managers
	 * base:stem:group:members => group
	 * base:stem:group:managers => group-managers
	 */
	public String getNakamuraGroupId(String grouperName) {
		int lastColon = grouperName.lastIndexOf(":");
		int secondToLastColon = grouperName.lastIndexOf(":", lastColon - 1);
		
		String groupId = grouperName.substring(secondToLastColon + 1, lastColon);
		String roleExtension = grouperName.substring(lastColon + 1);
		
		return groupId + "-" + roleExtension;
	}
}
