package org.sakaiproject.nakamura.grouper.changelog.util;

import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

/**
 * 
 * @author froese
 *
 */
public class BaseNakamuraGroupIdAdapter implements GroupIdAdapter {
	
	protected String basestem;
	
	public BaseNakamuraGroupIdAdapter(String basestem){
		this.basestem = basestem;
	}

	public String getNakamuraGroupId(String grouperName) {
		if (grouperName == null){
			return null;
		}
		return stripBaseStem(grouperName).replaceAll(":", "_");
	}

	public String getGrouperName(String nakamuraGroupName) {
		if (nakamuraGroupName == null){
			return null;
		}
		String grouperFullName = nakamuraGroupName.replaceAll("_", ":");
		if(basestem != null && ! "".equals(basestem)){
			grouperFullName = basestem + ":" + grouperFullName;
		}
		return grouperFullName;
	}
	
	protected String stripBaseStem(String grouperName){
		if (grouperName == null){
			return null;
		}
		if (grouperName.startsWith(basestem + ":")){
			grouperName = grouperName.substring(basestem.length() + 1);
		}
		return grouperName;
	}

}
