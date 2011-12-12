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
package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.AbstractGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.HttpNakamuraManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.TemplateGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdManager;
import org.sakaiproject.nakamura.grouper.changelog.util.ChangeLogUtils;

import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;

/**
 * Provision and sync Course Groups in Sakai OAE with Grouper.
 *
 * Courses in Sakai OAE have a group authorizable for each role in the Course.
 * At the time of this writing those were student, ta, lecturer.
 *
 * For each of these role groups we'll have a set of groups in Grouper.
 *
 * Example:
 *
 * Sakai OAE has a world named course0.
 *
 * Grouper would have the following:
 * 1. $COURSE:$ROLE
 * 2. $COURSE:$ROLE_includes
 * 3. $COURSE:$ROLE_excludes
 * 4. $COURSE:$ROLE_systemOfRecord
 * 5. $COURSE:$ROLE_systemOfRecordAndIncludes
 *
 * Group 1 would have the effective membership: ((4 + 5) + 2) - 3.
 *
 * This class should act on flattened membership events on $COURSE:$ROLE.
 * Since $COURSE:$ROLE is a composite group its membership depends on the
 * state of the component groups (and subgroups).
 */
public class WorldEsbConsumer extends AbstractWorldEsbConsumer {

	private static Log log = LogFactory.getLog(WorldEsbConsumer.class);

	/**
	 * Read the configuration from $GROUPER_HOME/conf/grouper-loader.properties.
	 *
	 * Set up the group and ID adapters.
	 *
	 * These calls are isolated here in an attempt to keep the component testable.
	 * In order to read the config files you should use the GrouperLoaderConfig
	 * static methods. Static methods were hard to mock until PowerMockito came along.
	 *
	 * This method should only read the config options once.
	 */
	protected void loadConfiguration(String consumerName) {
		if (configurationLoaded){
			return;
		}
		super.loadConfiguration(consumerName);

		SimpleGroupIdAdapter simpleAdapter = new SimpleGroupIdAdapter();
		simpleAdapter.loadConfiguration(consumerName);
		TemplateGroupIdAdapter templateAdapter = new TemplateGroupIdAdapter();
		templateAdapter.loadConfiguration(consumerName);

		GroupIdManagerImpl gidMgr = new GroupIdManagerImpl(); 
		gidMgr.loadConfiguration(consumerName);
		gidMgr.setSimpleGroupIdAdapter(simpleAdapter);
		gidMgr.setTemplateGroupIdAdapter(templateAdapter);
		groupIdManager = gidMgr;

		HttpNakamuraManagerImpl courseManager = new HttpNakamuraManagerImpl();
		courseManager.url = url;
		courseManager.username = username;
		courseManager.password = password;
		courseManager.createUsers = createUsers;
		courseManager.groupIdAdapter = groupIdManager;
		courseManager.dryrun = dryrun;
		courseManager.pseudoGroupSuffixes = pseudoGroupSuffixes;

		courseManager.firstNameAttribute = firstNameAttribute;
		courseManager.lastNameAttribute = lastNameAttribute;
		courseManager.emailAttribute = emailAttribute;
		courseManager.defaultEmailDomain = defaultEmailDomain;
		nakamuraManager = courseManager;
	}

	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		// Load up the necessary components
		String consumerName = changeLogProcessorMetadata.getConsumerName();
		loadConfiguration(consumerName);

		int entryCount = changeLogEntryList.size();
		log.info("Received a batch of " + entryCount + " entries : " +
				changeLogEntryList.get(0).getSequenceNumber() + " - " +
				changeLogEntryList.get(entryCount - 1).getSequenceNumber());

		long currentId = -1;

		try {
			for (ChangeLogEntry entry : changeLogEntryList) {
				currentId = entry.getSequenceNumber();
				log.info("Processing changelog entry=" + currentId); 
				if (!ignoreChangelogEntry(entry)){
					processChangeLogEntry(entry);
				}
			}
			log.info("Finished the batch of " + entryCount + " entries : " +
					changeLogEntryList.get(0).getSequenceNumber() + " - " +
					changeLogEntryList.get(entryCount - 1).getSequenceNumber());
		}
		// Stop processing changelog entries.
		catch (Exception e) {
			if (currentId == -1) {
				log.error("Didn't process any records.");
				throw new RuntimeException("Couldn't process any records");
			}
			changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
			// The last entry we successfully processed was the one before this
			return currentId - 1;
		}
		return currentId;
	}

	/**
	 * @param entry a change log entry
	 * @return whether or not to ignore this entry
	 */
	protected boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = false;
		Long sequenceNumber = entry.getSequenceNumber();
		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		if (log.isTraceEnabled()){
			log.trace(entry.toStringDeep());
		}
		if (grouperName == null){
			log.info("ignoring: " + sequenceNumber + " Unable to get the group name from the entry. ");
			ignore = true;
		}
		else {
			if (allowInstitutional == false && groupIdManager.isInstitutional(grouperName)){
				log.info("ignoring " + sequenceNumber + " : Not processing institutional data : " + grouperName);
				ignore = true;
			}
			if (!worldType.equals(groupIdManager.getWorldType(grouperName))){
				log.info("ignoring " + sequenceNumber + " : Not a " + worldType + " group : " + grouperName);
				ignore = true;
			}

			if (grouperName.endsWith(":" + AbstractGroupIdAdapter.ALL_GROUP_EXTENSION)){
				log.info("ignoring:  " + sequenceNumber + " all group: " + grouperName);
				ignore = true;
			}
		}
		return ignore;
	}
}