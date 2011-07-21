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

		if (extension.equals(SimpleGroupEsbConsumer.MANAGER_SUFFIX) || 
				extension.equals(SimpleGroupEsbConsumer.MEMBER_SUFFIX)){
			grouperName = stem;
		}
		else {
			// OAE must have updated the list of role groups. ugh
			return null;
		}

		if (grouperName.startsWith(provisionedSimpleGroupsStem)){
			nakamuraGroupId = grouperName.substring(provisionedSimpleGroupsStem.length()).replaceAll(":", "_");
			if (nakamuraGroupId.startsWith("_")){
				nakamuraGroupId = nakamuraGroupId.substring(1);
			}
		}

		if (extension.equals(SimpleGroupEsbConsumer.MANAGER_SUFFIX)){
			nakamuraGroupId += "-" + SimpleGroupEsbConsumer.MANAGER_SUFFIX;
		}
		else {
			nakamuraGroupId += "-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX;
		}

		return nakamuraGroupId;
	}

	public void setProvisionedSimpleGroupsStem(String simpleGroupsStem) {
		this.provisionedSimpleGroupsStem = simpleGroupsStem;
	}

}