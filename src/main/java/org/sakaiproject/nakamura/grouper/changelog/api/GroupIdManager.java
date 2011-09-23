package org.sakaiproject.nakamura.grouper.changelog.api;


public interface GroupIdManager extends GroupIdAdapter {
	
	public String getAllGroup(String grouperName);
	
	public String toProvisioned(String grouperName);
	 
	public boolean isCourseGroup(String grouperName);
	
	public boolean isSimpleGroup(String grouperName);
	
	public boolean isInstitutional(String grouperName);

	public boolean isIncludeExcludeSubGroup(String grouperName);

}