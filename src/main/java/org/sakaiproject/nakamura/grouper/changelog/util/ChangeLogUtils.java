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
package org.sakaiproject.nakamura.grouper.changelog.util;

import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;

public class ChangeLogUtils {
	
	/**
	 * Some events store the full grouper name on the name attribute, 
	 * others use grouperName.
	 * @param entry 
	 * @return the grouper name this entry references, null if not found or not supported.
	 */
	public static String getGrouperNameFromChangelogEntry(ChangeLogEntry entry){
		String grouperName = null;
		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)){
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_UPDATE)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.name);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_TYPE_ASSIGN)) {
			grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_TYPE_ASSIGN.groupName);
		}
		return grouperName;
	}
}
