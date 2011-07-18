package org.sakaiproject.nakamura.grouper.changelog;

import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraUtils;
import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public class SimpleGroupIdAdapter implements GroupIdAdapter {
	
	private static String PROP_SIMPLE_GROUPS_STEM = NakamuraUtils.PROPERTY_KEY_PREFIX + ".simplegroups.stem";
	
	private String simpleGroupsStem;
	
	public SimpleGroupIdAdapter(){
		simpleGroupsStem = GrouperLoaderConfig.getPropertyString(PROP_SIMPLE_GROUPS_STEM, true);
	}

	@Override
	public String getNakamuraGroupId(String grouperName) {
		String nakamuraGroupId = null;
		if (grouperName.startsWith(simpleGroupsStem)){
			nakamuraGroupId = grouperName.substring(simpleGroupsStem.length()).replaceAll(":", "_");
			if (nakamuraGroupId.startsWith("_")){
				nakamuraGroupId = nakamuraGroupId.substring(1);
			}
		}
		return nakamuraGroupId;
	}

	public void setSimpleGroupsStem(String simpleGroupsStem) {
		this.simpleGroupsStem = simpleGroupsStem;
	}

}
