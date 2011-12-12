/* Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.grouper.changelog.api;

import java.util.List;
import java.util.Map;

import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;

import edu.internet2.middleware.grouper.exception.GrouperException;

public interface NakamuraManager {

	/**
	 * Create a Sakai OAE World
	 * @param grouperName the full name (including hte tem) of this group in grouper
	 * @param worldId the id of the World in OAE
	 * @param title the title of the world.
	 * @param description a short description
	 * @param tags an array of tags to apply to the world
	 * @param visibility
	 * @param joinability
	 * @param template
	 * @param message
	 * @param usersRolesToAdd
	 * @throws GroupModificationException
	 */
	public void createWorld(String grouperName,
			String worldId,
			String title,
			String description,
			String[] tags,
			String visibility,
			String joinability,
			String template,
			String message,
			Map<String, String> usersRolesToAdd) throws GroupModificationException;

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
	public void setProperty(String groupId, String key, String value) throws GroupModificationException;

	/**
	 * Set properties on a group in OAE
	 * @param groupId the id of the group in OAE
	 * @param properties a map of key,value pairs to add to or overwrite on the OAE group
	 * @throws GroupModificationException
	 */
	public void setProperties(String groupId, Map<String,String> properties) throws GroupModificationException;

	/**
	 * Get the list of roles for this group.
	 * This is the value of the sakai:roles property
	 * @param worldId
	 * @return
	 */
	public List<String> getRoles(String worldId);
}
