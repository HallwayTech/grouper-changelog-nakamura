package org.sakaiproject.nakamura.grouper.changelog.api;


public interface GroupIdManager extends GroupIdAdapter {
	
	/**
	 * @param grouperName the name of a Grouper group
	 * @return the group with the same stem but the extension all
	 */
	public String getAllGroup(String grouperName);
	
	/**
	 * @param grouperName the name of a Grouper group
	 * @return true if this grouperName represents part of a Course in OAE
	 */
	public boolean isCourseGroup(String grouperName);
	
	/**
	 * @param grouperName the name of a Grouper group
	 * @return if this is a sub-group in an addIncludeExclude group structure.
	 */
	public boolean isIncludeExcludeSubGroup(String grouperName);
	
	/**
	 * @param grouperName the name of a Grouper group
	 * @return if this grouperName is part of the institutional stem.
	 */
	public boolean isInstitutional(String grouperName);

	/**
	 * @param grouperName the name of a Grouper group
	 * @return true if this grouperName represents part of a Simple Group in OAE
	 */
	public boolean isSimpleGroup(String grouperName);

	/**
	 * @param grouperName the name of a Grouper group
	 * @return the corresponding grouper name in the application-provisioned stem
	 */
	public String toProvisioned(String grouperName);
}