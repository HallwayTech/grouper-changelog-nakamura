package org.sakaiproject.nakamura.grouper.changelog;

import java.util.List;

import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UnsupportedGroupException;
import org.sakaiproject.nakamura.grouper.changelog.util.LastStemGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.util.StaticInitialGroupPropertiesProvider;
import org.sakaiproject.nakamura.grouper.changelog.util.api.NakamuraUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;

/**
 * Process changelog entries and update group information in sakai3-nakamura
 */
public class NakamuraEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = GrouperUtil.getLog(NakamuraEsbConsumer.class);
	
	private HttpNakamuraGroupAdapter nakamuraGroupAdapter;

	private GrouperSession grouperSession;

	private Subject grouperSystemSubject;
	
	private String[] supportedStems = new String[] { "nyu:apps:atlas" };
	
	public NakamuraEsbConsumer(){
		super();
		nakamuraGroupAdapter = new HttpNakamuraGroupAdapter();
		nakamuraGroupAdapter.setUrl(GrouperLoaderConfig.getPropertyString(NakamuraUtils.PROPERTY_KEY_PREFIX + ".url", true));
		nakamuraGroupAdapter.setUsername(GrouperLoaderConfig.getPropertyString(NakamuraUtils.PROPERTY_KEY_PREFIX + ".username", true));
		nakamuraGroupAdapter.setPassword(GrouperLoaderConfig.getPropertyString(NakamuraUtils.PROPERTY_KEY_PREFIX + ".password", true));
		nakamuraGroupAdapter.setInitialPropertiesProvider(new StaticInitialGroupPropertiesProvider());
		nakamuraGroupAdapter.setGroupIdAdapter(new LastStemGroupIdAdapter(GrouperLoaderConfig.getPropertyString(NakamuraUtils.PROPERTY_KEY_PREFIX + ".basestem", true)));
	}

	/**
	 * @see edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase#processChangeLogEntries(List, ChangeLogProcessorMetadata)
	 */
	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		long currentId = -1;

		// try catch so we can track that we made some progress
		try {
			for (ChangeLogEntry changeLogEntry : changeLogEntryList) {
				currentId = changeLogEntry.getSequenceNumber();

				if (log.isDebugEnabled()){
					log.info("Processing changelog entry=" + currentId);
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_ADD + ": name=" + groupName);
					}
					checkSupportedGroup(groupName);
					Group group = GroupFinder.findByName(getGrouperSession(), groupName, false);

					// Nakamura creates the -managers groups on its own.
					if (group != null && !group.getExtension().equals("managers")){
						nakamuraGroupAdapter.createGroup(group);
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.id);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_DELETE+ ": name=" + groupName);
					}
					checkSupportedGroup(groupName);
					Group group = GroupFinder.findByName(getGrouperSession(), groupName, false);
					if (group == null){
						nakamuraGroupAdapter.deleteGroup(groupId, groupName);
					}
					else {
						log.error("Received a delete event for a group that still exists!");
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_UPDATE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.id);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.name);
					String propertyName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.propertyChanged);
					String oldValue = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.propertyOldValue);
					String newValue = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.propertyNewValue);
					checkSupportedGroup(groupName);
					// TODO implement updateProperty
					// nakamuraGroupAdapter.updateProperty(groupId, propertyName, oldValue, newValue);
					log.debug("Group update, name: "  + groupId + ", property: " + propertyName
							+ ", from: '" + oldValue + "', to: '" + newValue + "'");
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupId);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
					String subjectId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					log.debug("Membership add, name: " + groupName + " subjectId: " + subjectId);
					checkSupportedGroup(groupName);
					nakamuraGroupAdapter.addMembership(groupId, groupName, subjectId);
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupId);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String subjectId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					log.debug("Membership delete, name: " + groupName + " subjectId: " + subjectId);
					checkSupportedGroup(groupName);
					nakamuraGroupAdapter.deleteMembership(groupId, groupName, subjectId);
				}
				// we successfully processed this record
			}
		}
		catch(UnsupportedGroupException e){
			log.error(e.getMessage());
		}
		catch (Exception e) {
			changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
			//we made it to this -1
			return currentId - 1;
		}
		if (currentId == -1) {
			throw new RuntimeException("Couldn't process any records");
		}
		return currentId;
	}
	
	private void checkSupportedGroup(String groupName) throws UnsupportedGroupException {
		boolean supported = false;
		
		for (int i = 0; i < supportedStems.length; i++){
			if (groupName.startsWith(supportedStems[i])) {
				supported = true;
				break;
			}
		}
		if (!supported){
			throw new UnsupportedGroupException("Not configured to handle " + groupName + ". Check the elfilter.");
		}
	}

	/**
	 * Lazy-load the grouperSession 
	 * @return
	 */
	private GrouperSession getGrouperSession(){
		if ( grouperSession == null || grouperSystemSubject == null ) {
			try {
				grouperSystemSubject = SubjectFinder.findRootSubject();
				grouperSession = GrouperSession.start(grouperSystemSubject, false);
				log.debug("started session: " + this.grouperSession);
			}
			catch (SessionException se) {
				throw new GrouperException("Error starting session: " + se.getMessage(), se);
			}
		}
		return grouperSession;
	}

	public void setNakamuraGroupAdapter(HttpNakamuraGroupAdapter nakamuraGroupAdapter) {
		this.nakamuraGroupAdapter = nakamuraGroupAdapter;
	}
}