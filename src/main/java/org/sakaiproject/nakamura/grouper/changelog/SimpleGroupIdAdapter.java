package org.sakaiproject.nakamura.grouper.changelog;

import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

public class SimpleGroupIdAdapter implements GroupIdAdapter {

	private String provisionedSimpleGroupsStem;

	private static final String MANAGER_SUFFIX = "manager";
	private static final String MEMBER_SUFFIX = "member";

	@Override
	public String getNakamuraGroupId(String grouperName) {
		String nakamuraGroupId = null;
		int lastColon = grouperName.lastIndexOf(':');
		String stem = grouperName.substring(0, lastColon);
		String extension = grouperName.substring(lastColon + 1);

		if (extension.equals(MANAGER_SUFFIX) || extension.equals(MEMBER_SUFFIX)){
			grouperName = stem;
		}

		if (grouperName.startsWith(provisionedSimpleGroupsStem)){
			nakamuraGroupId = grouperName.substring(provisionedSimpleGroupsStem.length()).replaceAll(":", "_");
			if (nakamuraGroupId.startsWith("_")){
				nakamuraGroupId = nakamuraGroupId.substring(1);
			}
		}

		if (extension.equals(MANAGER_SUFFIX)){
			nakamuraGroupId += "-" + MANAGER_SUFFIX;
		}

		return nakamuraGroupId;
	}

	public void setProvisionedSimpleGroupsStem(String simpleGroupsStem) {
		this.provisionedSimpleGroupsStem = simpleGroupsStem;
	}

}