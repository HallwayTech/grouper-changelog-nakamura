package org.sakaiproject.nakamura.grouper.changelog.api;

import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.exception.GrouperException;

public interface NakamuraGroupAdapter {
	
	/**
	 * Create a group.
	 * @param groupId the internal grouper id
	 * @param groupName the full name of the group (includes stem)
	 * @throws GroupModificationException
	 */
	public void createGroup(Group group) throws GroupModificationException;
	
	/**
	 * Delete a group.
	 * @param groupId the internal grouper id
	 * @param groupName the full name of the group (includes stem)
	 * @throws GroupModificationException
	 */
	public void deleteGroup(String groupId, String groupName) throws GroupModificationException;
	
	/**
	 * Add a subject to a group.
	 * @param groupId the internal grouper id
	 * @param groupName the full name of the group (includes stem)
	 * @param subjectId the id of the subject being added
	 * @throws GroupModificationException
	 */
	public void addMembership(String groupId, String groupName, String subjectId) throws GroupModificationException;
	
	/**
	 * Remove a subject from a group.
	 * @param groupId the internal grouper id
	 * @param groupName the full name of the group (includes stem)
	 * @param subjectId the id of the subject being removed
	 * @throws GroupModificationException
	 */
	public void deleteMembership(String groupId, String groupName, String subjectId) throws GroupModificationException;

	/**
	 * Does the group exist in SakaiOAE?
	 * @param groupId
	 * @return whether or not the group already exists.
	 */
	public boolean groupExists(String groupId) throws GrouperException;
}
