package org.sakaiproject.nakamura.grouper.changelog.util.api;

/**
 * A strategy to map names from grouper to Nakamura.
 */
public interface GroupIdAdapter {

	/**
	 * Get the ID of a group in Nakamura given the full group name from Grouper.
	 * @param grouperName the full group id in grouper ex: stem1:stem2:groupName
	 * @return the group ID in nakamura ex: stem1_stem2_groupName
	 */
	public String getNakamuraGroupId(String grouperName);

	/**
	 * Given a nakamura group id, return the parent of this group.
	 * If the group is not a psuedo group the groupId will be returned.
	 * @param groupId
	 * @return
	 */
	public String getPseudoGroupParent(String groupId);

}