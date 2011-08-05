package org.sakaiproject.nakamura.grouper.changelog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public abstract class BaseGroupIdAdapter {

	public static final String PROP_NAKID_ROLE_MAPPINGS = "TemplateGroupIdAdapter.role.map";

	protected Set<String> pseudoGroupSuffixes;
	protected Set<String> includeExcludeSuffixes;
	protected Map<String,String> roleMap;

	// addIncludeExclude group suffixes
	public static final String PROP_SYSTEM_OF_RECORD_SUFFIX = "grouperIncludeExclude.systemOfRecord.extension.suffix";
	public static final String PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "grouperIncludeExclude.systemOfRecordAndIncludes.extension.suffix";
	public static final String PROP_INCLUDES_SUFFIX = "grouperIncludeExclude.include.extension.suffix";
	public static final String PROP_EXCLUDES_SUFFIX = "grouperIncludeExclude.exclude.extension.suffix";

	public static final String DEFAULT_SYSTEM_OF_RECORD_SUFFIX = "_systemOfRecord";
	public static final String DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "_systemOfRecordAndIncludes";
	public static final String DEFAULT_INCLUDES_SUFFIX = "_includes";
	public static final String DEFAULT_EXCLUDES_SUFFIX = "_excludes";

	public void loadConfiguration(String consumerName) {
		String cfgPrefix = "changeLog.consumer." + consumerName + ".";
		setRoleMap(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_NAKID_ROLE_MAPPINGS, true));

		includeExcludeSuffixes = new HashSet<String>();
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_INCLUDES_SUFFIX, DEFAULT_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_EXCLUDES_SUFFIX, DEFAULT_EXCLUDES_SUFFIX));

		pseudoGroupSuffixes = new HashSet<String>();
		String str = GrouperLoaderConfig.getPropertyString(cfgPrefix + "psuedoGroup.suffixes");
		for(String suffix: StringUtils.split(str, ",")){
			pseudoGroupSuffixes.add(suffix.trim());
		}
	}

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

	public void setRoleMap(String propertyString) {
		roleMap = new HashMap<String,String>();
		for(String map: StringUtils.split(propertyString, ",")){
			String[] m = StringUtils.split(map.trim(), ":");
			roleMap.put(m[0], m[1]);
		}
	}

}
