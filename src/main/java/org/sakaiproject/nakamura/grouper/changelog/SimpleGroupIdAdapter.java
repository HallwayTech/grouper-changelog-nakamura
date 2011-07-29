package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;

import org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer;
import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

public class SimpleGroupIdAdapter implements GroupIdAdapter {

	private String provisionedSimpleGroupsStem;
	private String adhocSimpleGroupsStem;

	private Set<String> pseudoGroupSuffixes;
	private Set<String> includeExcludeSuffixes;

	@Override
	public String getNakamuraGroupId(String grouperName) {
		String nakamuraGroupId = null;

		if (grouperName.startsWith(provisionedSimpleGroupsStem)){
			nakamuraGroupId = grouperName.substring(provisionedSimpleGroupsStem.length()).replaceAll(":", "_");
		} else if (grouperName.startsWith(provisionedSimpleGroupsStem)){
			nakamuraGroupId = grouperName.substring(adhocSimpleGroupsStem.length() + 6).replaceAll(":", "_");
		}
		if (nakamuraGroupId.startsWith("_")){
			nakamuraGroupId = nakamuraGroupId.substring(1);
		}

		// If the groupername ends in _SUFFIX we change that to -SUFFIX
		for (String suffix: pseudoGroupSuffixes){
			if (nakamuraGroupId.endsWith("_" + suffix)){
				nakamuraGroupId = nakamuraGroupId.substring(0, nakamuraGroupId.lastIndexOf("_")) + "-" + suffix;
				break;
			}
			// If the groupername ends in _SUFFIX_systemOfRecord we change that to -SUFFIX
			for (String ieSuffix: includeExcludeSuffixes) {
				if (nakamuraGroupId.endsWith("_" + suffix + ieSuffix)){
					nakamuraGroupId = nakamuraGroupId.substring(0, nakamuraGroupId.lastIndexOf("_"));
					int newlast = nakamuraGroupId.lastIndexOf("_");
					nakamuraGroupId = nakamuraGroupId.substring(0,newlast) + "-" + nakamuraGroupId.substring(newlast + 1);
					break;
				}
			}
		}

		if (!nakamuraGroupId.endsWith(SimpleGroupEsbConsumer.MANAGER_SUFFIX) &&
			!nakamuraGroupId.endsWith(SimpleGroupEsbConsumer.MEMBER_SUFFIX)){
			return null;
		}
		return nakamuraGroupId;
	}

	public void setProvisionedSimpleGroupsStem(String simpleGroupsStem) {
		this.provisionedSimpleGroupsStem = simpleGroupsStem;
	}

	public void setAdhocSimpleGroupsStem(String adhocGroupsStem) {
		this.adhocSimpleGroupsStem = adhocGroupsStem;
	}

	public void setPseudoGroupSuffixes(Set<String> pseudoGroupSuffixes) {
		this.pseudoGroupSuffixes = pseudoGroupSuffixes;
	}

	public void setIncludeExcludeSuffixes(Set<String> includeExcludeSuffixes) {
		this.includeExcludeSuffixes = includeExcludeSuffixes;
	}
}