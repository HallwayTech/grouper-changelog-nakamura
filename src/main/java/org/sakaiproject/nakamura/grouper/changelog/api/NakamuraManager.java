package org.sakaiproject.nakamura.grouper.changelog.api;

import java.util.List;

import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;

import edu.internet2.middleware.grouper.exception.GrouperException;

public interface NakamuraManager {

	/**
	 * Create a Sakai OAE World
	 * @param groupName the full name of the group (includes stem)
	 * @param description description for the group
	 * @throws GroupModificationException
	 */
	public void createWorld(String groupName, String description) throws GroupModificationException;

	/**
	 * Create a user in Sakai OAE
	 * @param userId the id of the User
	 * @throws UserModificationException
	 */
	public void createUser(String userId) throws UserModificationException;

	/**
	 * Add a subject to a group.
	 * @param groupId the id of the group in OAE
	 * @param subjectId the id of the subject being added
	 * @throws GroupModificationException
	 * @throws UserModificationException
	 */
	public void addMembership(String groupId, String subjectId) throws GroupModificationException, UserModificationException;

	/**
	 * Add a list of subject sto a group.
	 * @param groupId the id of the group in OAE
	 * @param subjectIds the ids of the subjects being added
	 * @throws GroupModificationException
	 * @throws UserModificationException
	 */
	public void addMemberships(String groupId, List<String> subjectIds) throws GroupModificationException, UserModificationException;

	/**
	 * Delete a group from Sakai OAE
	 * @param groupId the id of the group in OAE
	 * @param groupName the full name of the group (includes stem)
	 * @throws GroupModificationException
	 */
	public void deleteGroup(String groupId, String groupName) throws GroupModificationException;

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
