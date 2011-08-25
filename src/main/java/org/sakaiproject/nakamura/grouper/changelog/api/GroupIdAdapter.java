package org.sakaiproject.nakamura.grouper.changelog.api;

/**
 * A strategy to map names from grouper to Nakamura.
 */
public interface GroupIdAdapter {

	/**
	 * Get the ID of a group in Nakamura given the full group name from Grouper.
	 * @param grouperName the full group id in grouper ex: stem1:stem2:groupName
	 * @return the group ID in nakamura ex: stem1_stem2_groupName
	 */
	public String getGroupId(String grouperName);

	/**
	 * Given a nakamura group id, return the parent of this group.
	 * If the group is not a pseudo group the groupId will be returned.
	 * @param groupId
	 * @return
	 */
	public String getPseudoGroupParent(String groupId);

}