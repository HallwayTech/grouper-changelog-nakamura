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

/**
 * A strategy to map names from grouper to Sakai OAE.
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
	public String getWorldId(String groupId);
}