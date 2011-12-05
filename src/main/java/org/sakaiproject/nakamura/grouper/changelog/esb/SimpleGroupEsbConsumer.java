package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.BaseGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpNakamuraManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.util.ChangeLogUtils;

import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;

/**
 * Provision Simple Groups and memberships to Sakai OAE.
 */
public class SimpleGroupEsbConsumer extends BaseGroupEsbConsumer {

	private static Log log = LogFactory.getLog(SimpleGroupEsbConsumer.class);

	public static final String MANAGER_SUFFIX = "manager";
	public static final String MEMBER_SUFFIX = "member";

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
		
		GroupIdManagerImpl gidMgr = new GroupIdManagerImpl();
		gidMgr.loadConfiguration(consumerName);
		gidMgr.setSimpleGroupIdAdapter(simpleAdapter);
		groupIdManager = gidMgr;

		HttpNakamuraManagerImpl simpleManager = new HttpNakamuraManagerImpl();
		simpleManager.url = url;
		simpleManager.username = username;
		simpleManager.password = password;
		simpleManager.createUsers = createUsers;
		simpleManager.groupIdAdapter = groupIdManager;
		simpleManager.dryrun = dryrun;
		simpleManager.pseudoGroupSuffixes = pseudoGroupSuffixes;

		simpleManager.firstNameAttribute = firstNameAttribute;
		simpleManager.lastNameAttribute = lastNameAttribute;
		simpleManager.emailAttribute = emailAttribute;
		simpleManager.defaultEmailDomain = defaultEmailDomain;
		nakamuraManager = simpleManager;
	}

	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		String consumerName = changeLogProcessorMetadata.getConsumerName();
		loadConfiguration(consumerName);

		int entryCount = changeLogEntryList.size();
		log.info("Received a batch of " + entryCount + " entries : " +
				changeLogEntryList.get(0).getSequenceNumber() + " - " +
				changeLogEntryList.get(entryCount - 1).getSequenceNumber());

		long currentId = -1;

		try {
			// try catch so we can track that we made some progress
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
	public boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = false;
		Long sequenceNumber = entry.getSequenceNumber();
		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		if (grouperName == null){
			log.debug("ignoring: Unable to get the group name from the entry : " + entry.toStringDeep());
			ignore = true;
		}
		else {

			if (allowInstitutional == false && groupIdManager.isInstitutional(grouperName)){
				log.debug("ignoring: Not processing institutional data : " + grouperName);
				ignore = true;
			}

			if (!groupIdManager.isSimpleGroup(grouperName)){
				log.debug("ignoring: Not a simple group : " + grouperName);
				ignore = true;
			}

			if (grouperName.endsWith(":" + BaseGroupIdAdapter.ALL_GROUP_EXTENSION)){
				log.info("ignoring:  " + sequenceNumber + " all group: " + grouperName);
				ignore = true;
			}
		}
		return ignore;
	}
}