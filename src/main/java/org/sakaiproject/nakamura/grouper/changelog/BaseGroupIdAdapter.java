package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;

public abstract class BaseGroupIdAdapter {
	
	protected Set<String> pseudoGroupSuffixes;
	protected Set<String> includeExcludeSuffixes;
	
	/**
	 * Return the authorizableId of the parent group for this group.
	 * @param nakamuraGroupId
	 * @return
	 */
	public String getPseudoGroupParent(String nakamuraGroupId){
		int dash = nakamuraGroupId.lastIndexOf("-");
		if (dash != -1){
			String afterDash = nakamuraGroupId.substring(dash + 1);
			if (pseudoGroupSuffixes.contains(afterDash)){
				nakamuraGroupId =  nakamuraGroupId.substring(0, dash);
			}
		}
		return nakamuraGroupId;
	}

	public void setPseudoGroupSuffixes(Set<String> pseudoGroupSuffixes) {
		this.pseudoGroupSuffixes = pseudoGroupSuffixes;
	}

	public void setIncludeExcludeSuffixes(Set<String> includeExcludeSuffixes) {
		this.includeExcludeSuffixes = includeExcludeSuffixes;
	}

}
