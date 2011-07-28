package org.sakaiproject.nakamura.grouper.changelog.util;

import java.util.HashSet;
import java.util.Set;

import org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public class NakamuraUtils {

	protected static Set<String> psuedoGroupSuffixes = new HashSet<String>();

	/**
	 * @return is this group part of a course group in OAE?
	 */
	public static boolean isCourseGroup(Group group){
		String ext = group.getExtension();
		return _isCourseGroup(ext);
	}

	/**
	 * @return is this group part of a course group in OAE?
	 */
	public static boolean isCourseGroup(String grouperName){
		return _isCourseGroup(grouperName.substring(grouperName.lastIndexOf(':') + 1));
	}

	private static boolean _isCourseGroup(String extension){
		for (String suffix : psuedoGroupSuffixes){
			if (extension.startsWith(suffix) && !"manager".equals(suffix)){
				return true;
			}
		}
		return false;
	}

	/**
	 * TODO: Make this testable
	 * @return is this group part of a "Simple Group" in OAE?
	 */
	public static boolean isSimpleGroup(Group group){
		if (group == null){
			return false;
		}
		return isSimpleGroup(group.getName());
	}

	/**
	 * @return is this group part of a "Simple Group" in OAE?
	 */
	public static boolean isSimpleGroup(String grouperName){
		String provisioned = GrouperLoaderConfig.getPropertyString(
				SimpleGroupEsbConsumer.PROP_PROVISIONED_SIMPLEGROUPS_STEM);
		String adhoc = GrouperLoaderConfig.getPropertyString(
				SimpleGroupEsbConsumer.PROP_PROVISIONED_SIMPLEGROUPS_STEM);
		return grouperName.startsWith(provisioned) || grouperName.startsWith(adhoc); 
	}
}
