package org.sakaiproject.nakamura.grouper.changelog;

import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

public class SimpleGroupIdAdapter implements GroupIdAdapter {

	private String provisionedSimpleGroupsStem;

	@Override
	public String getNakamuraGroupId(String grouperName) {
		String nakamuraGroupId = null;
		int lastColon = grouperName.lastIndexOf(':');
		String stem = grouperName.substring(0, lastColon);
		String extension = grouperName.substring(lastColon + 1);
		if (grouperName.startsWith(provisionedSimpleGroupsStem)){
			nakamuraGroupId = grouperName.substring(provisionedSimpleGroupsStem.length()).replaceAll(":", "_");
			if (nakamuraGroupId.startsWith("_")){
				nakamuraGroupId = nakamuraGroupId.substring(1);
			}
		}
		return nakamuraGroupId;
	}

	public void setProvisionedSimpleGroupsStem(String simpleGroupsStem) {
		this.provisionedSimpleGroupsStem = simpleGroupsStem;
	}

}