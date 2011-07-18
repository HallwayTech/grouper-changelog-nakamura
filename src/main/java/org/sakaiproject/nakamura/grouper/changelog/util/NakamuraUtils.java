package org.sakaiproject.nakamura.grouper.changelog.util.api;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public class NakamuraUtils {

	public static final String PROPERTY_KEY_PREFIX = "nakamura";

	private static Set<String> psuedoGroupSuffixes = new HashSet<String>();

	/**
	 * @return the pseudo group suffixes from sakai
	 * TODO: Find a better way to get these values. It'd be nice if sakai
	 * stored them somewhere available via HTTP.
	 */
	public static Set<String> getPsuedoGroupSuffixes(){
		String str = GrouperLoaderConfig.getPropertyString(PROPERTY_KEY_PREFIX + ".psuedoGroup.suffixes");
		for(String suffix: StringUtils.split(str, ",")){
			psuedoGroupSuffixes.add(suffix.trim());
		}
		return psuedoGroupSuffixes;
	}
}
