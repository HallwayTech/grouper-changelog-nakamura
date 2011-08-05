package org.sakaiproject.nakamura.grouper.changelog;

import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdAdapter;

public class SimpleGroupIdAdapter extends BaseGroupIdAdapter implements GroupIdAdapter {

	@Override
	public String getGroupId(String grouperName) {
		String nakamuraGroupId = grouperName.replaceAll(":", "_");

		if (nakamuraGroupId.startsWith("_")){
			nakamuraGroupId = nakamuraGroupId.substring(1);
		}

		// If the groupername ends in _SUFFIX we change that to -SUFFIX
		for (String psSuffix: pseudoGroupSuffixes){
			String nakamuraSuffix = roleMap.get(psSuffix);
			if (nakamuraSuffix == null){
				nakamuraSuffix = psSuffix;
			}
			if (nakamuraGroupId.endsWith("_" + psSuffix)){
				nakamuraGroupId = nakamuraGroupId.substring(0, nakamuraGroupId.lastIndexOf("_")) + "-" + psSuffix;
				break;
			}
			// If the groupername ends in _SUFFIX_systemOfRecord we change that to -SUFFIX
			for (String ieSuffix: includeExcludeSuffixes) {
				if (nakamuraGroupId.endsWith("_" + psSuffix + ieSuffix)){
					nakamuraGroupId = nakamuraGroupId.substring(0, nakamuraGroupId.lastIndexOf("_"));
					int newlast = nakamuraGroupId.lastIndexOf("_");
					nakamuraGroupId = nakamuraGroupId.substring(0,newlast) + "-" + nakamuraGroupId.substring(newlast + 1);
					break;
				}
			}
		}

		return nakamuraGroupId;
	}
}