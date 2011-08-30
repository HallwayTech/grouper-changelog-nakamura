package org.sakaiproject.nakamura.grouper.changelog.api;

import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;

import edu.internet2.middleware.grouper.exception.GrouperException;

public interface NakamuraGroupAdapter {

	/**
	 * Create a group in Sakai OAE
	 * @param groupName the full name of the group (includes stem)
	 * @param the description for the group
	 * @throws GroupModificationException
	 */
	public void createGroup(String groupName, String description) throws GroupModificationException;

	/**
	 * Delete a group from Sakai OAE
	 * @param groupId the id of the group in OAE
	 * @param groupName the full name of the group (includes stem)
	 * @throws GroupModificationException
	 */
	public void deleteGroup(String groupId, String groupName) throws GroupModificationException;

	/**
	 * Add a subject to a group.
	 * @param groupId the id of the group in OAE
	 * @param subjectId the id of the subject being added
	 * @throws GroupModificationException
	 */
	public void addMembership(String groupId, String subjectId) throws GroupModificationException, UserModificationException;

	/**
	 * Remove a subject from a group.
	 * @param groupId the id of the group in OAE
	 * @param subjectId the id of the subject being removed
	 * @throws GroupModificationException
	 */
	public void deleteMembership(String groupId, String subjectId) throws GroupModificationException;

	/**
	 * Does the group exist in SakaiOAE?
	 * @param groupId the id of the group in OAE
	 * @return whether or not the group already exists.
	 */
	public boolean groupExists(String groupId) throws GrouperException;

	/**
	 * Set a property on a group in Sakai OAE
	 * @param groupId the id of the group in OAE
	 * @param key the property key
	 * @param value the value to store
	 * @throws GrouperException
	 */
	public void setProperty(String groupName, String key, String value) throws GroupModificationException;
}
