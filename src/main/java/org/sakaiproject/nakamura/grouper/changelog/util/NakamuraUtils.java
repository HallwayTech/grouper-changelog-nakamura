package org.sakaiproject.nakamura.grouper.changelog.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public class NakamuraUtils {

	protected static Set<String> psuedoGroupSuffixes = new HashSet<String>();

	protected static String simpleGroupPattern = GrouperLoaderConfig.getPropertyString("nakamura.simplegroups.regex", true);
	protected static String courseGroupPattern = GrouperLoaderConfig.getPropertyString("nakamura.coursegroups.regex", true);
	protected static String contactGroupPattern = GrouperLoaderConfig.getPropertyString("nakamura.contactgroups.regex", true);

	/**
	 * @return is this group part of a course group in OAE?
	 */
	public static boolean isCourseGroup(Group group){
		return isCourseGroup(group.getName());
	}

	/**
	 * @return is this group part of a course group in OAE?
	 */
	public static boolean isCourseGroup(String grouperName){
		return Pattern.matches(courseGroupPattern, grouperName);
	}

	/**
	 * @return is this group part of a course group in OAE?
	 */
	public static boolean isSimpleGroup(Group group){
		return isSimpleGroup(group.getName());
	}

	/**
	 * @return is this group part of a "Simple Group" in OAE?
	 */
	public static boolean isSimpleGroup(String grouperName){
		return Pattern.matches(simpleGroupPattern, grouperName);
	}

	/**
	 * @return is this group part of a course group in OAE?
	 */
	public static boolean isContactGroup(Group group){
		return isContactGroup(group.getName());
	}

	/**
	 * @return is this group part of a "Simple Group" in OAE?
	 */
	public static boolean isContactGroup(String grouperName){
		return Pattern.matches(contactGroupPattern, grouperName);
	}
}
