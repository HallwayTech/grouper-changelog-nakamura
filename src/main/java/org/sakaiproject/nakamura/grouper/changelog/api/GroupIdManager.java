package org.sakaiproject.nakamura.grouper.changelog.api;


public interface GroupIdManager extends GroupIdAdapter {
	
	public static final String COURSE = "course";
	public static final String SIMPLE = "simple";

	/**
	 * @param grouperName the name of a Grouper group
	 * @return the group with the same stem but the extension all
	 */
	public String getAllGroupName(String grouperName);

	/**
	 * @param grouperName the name of a Grouper group
	 * @return the corresponding grouper name in the application-provisioned stem
	 */
	public String getApplicationGroupName(String grouperName);

	/**
	 * @param grouperName the name of a Grouper group
	 * @return the type of group this is
	 */
	public String getWorldType(String grouperName);
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
}