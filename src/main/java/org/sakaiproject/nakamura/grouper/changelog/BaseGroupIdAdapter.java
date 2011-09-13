package org.sakaiproject.nakamura.grouper.changelog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.sakaiproject.nakamura.grouper.changelog.esb.BaseGroupEsbConsumer;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public abstract class BaseGroupIdAdapter {

	protected Set<String> pseudoGroupSuffixes;
	protected Set<String> includeExcludeSuffixes;
	protected Map<String,String> roleMap;

	public static final String PROP_NAKID_ROLE_MAPPINGS = "role.map";

	// addIncludeExclude group suffix configs
	public static final String PROP_SYSTEM_OF_RECORD_SUFFIX = "grouperIncludeExclude.systemOfRecord.extension.suffix";
	public static final String PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "grouperIncludeExclude.systemOfRecordAndIncludes.extension.suffix";
	public static final String PROP_INCLUDES_SUFFIX = "grouperIncludeExclude.include.extension.suffix";
	public static final String PROP_EXCLUDES_SUFFIX = "grouperIncludeExclude.exclude.extension.suffix";

	public static final String DEFAULT_SYSTEM_OF_RECORD_SUFFIX = "_systemOfRecord";
	public static final String DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "_systemOfRecordAndIncludes";
	public static final String DEFAULT_INCLUDES_SUFFIX = "_includes";
	public static final String DEFAULT_EXCLUDES_SUFFIX = "_excludes";

	public static final String ALL_GROUP_EXTENSION = "all";

	public void loadConfiguration(String consumerName) {
		String cfgPrefix = BaseGroupEsbConsumer.CONFIG_PREFIX + "." + consumerName + ".";
		setRoleMap(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_NAKID_ROLE_MAPPINGS, true));
		setPseudoGroupSuffixes(GrouperLoaderConfig.getPropertyString(cfgPrefix + BaseGroupEsbConsumer.PROP_PSEUDOGROUP_SUFFIXES, true));

		includeExcludeSuffixes = new HashSet<String>();
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_INCLUDES_SUFFIX, DEFAULT_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_EXCLUDES_SUFFIX, DEFAULT_EXCLUDES_SUFFIX));
	}

	/**
	 * Is this a sub group of the addIncludeExclude group?
	 * @param grouperName
	 * @return
	 */
	public boolean isIncludeExcludeSubGroup(String grouperName){
		for (String suffix: includeExcludeSuffixes){
			if (grouperName.endsWith(suffix)){
				return true;
			}
		}
		return false;
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

	public void setPseudoGroupSuffixes(String propertyString) {
		pseudoGroupSuffixes = new HashSet<String>();
		for(String suffix: StringUtils.split(propertyString, ",")){
			pseudoGroupSuffixes.add(suffix.trim());
		}
	}

	public void setIncludeExcludeSuffixes(Set<String> includeExcludeSuffixes) {
		this.includeExcludeSuffixes = includeExcludeSuffixes;
	}

	public void setRoleMap(String propertyString) {
		roleMap = new HashMap<String,String>();
		for(String map: StringUtils.split(propertyString, ",")){
			String[] m = StringUtils.split(map.trim(), ":");
			if (m.length == 2){
				roleMap.put(m[0], m[1]);
			}
		}
	}

}
