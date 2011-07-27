package org.sakaiproject.nakamura.grouper.changelog;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UnsupportedGroupException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;

/**
 * Provision Simple Groups and memberships to Sakai OAE.
 */
public class SimpleGroupEsbConsumer extends BaseGroupEsbConsumer {

	private static Log log = GrouperUtil.getLog(SimpleGroupEsbConsumer.class);

	// The interface to the SakaiOAE/nakamura server.
	private HttpSimpleGroupAdapter simpleGroupAdapter;

	// This job will try to process events for groups in these stems
	private Set<String> supportedStems;

	// Decides where we accept events from
	public static final String PROP_ADHOC_SIMPLEGROUPS_STEM =  PROPERTY_KEY_PREFIX + ".simplegroups.adhoc.stem";
	public static final String PROP_PROVISIONED_SIMPLEGROUPS_STEM =  PROPERTY_KEY_PREFIX + ".simplegroups.provisioned.stem";

	public static final String MANAGER_SUFFIX = "manager";
	public static final String MEMBER_SUFFIX = "member";

	public SimpleGroupEsbConsumer() throws MalformedURLException {
		super();

		supportedStems = new HashSet<String>();
		supportedStems.add(GrouperLoaderConfig.getPropertyString(PROP_ADHOC_SIMPLEGROUPS_STEM, true));
		supportedStems.add(GrouperLoaderConfig.getPropertyString(PROP_PROVISIONED_SIMPLEGROUPS_STEM, true));

		SimpleGroupIdAdapter groupIdAdapter = new SimpleGroupIdAdapter();
		groupIdAdapter.setProvisionedSimpleGroupsStem(GrouperLoaderConfig.getPropertyString(PROP_PROVISIONED_SIMPLEGROUPS_STEM, true));

		simpleGroupAdapter = new HttpSimpleGroupAdapter();
		simpleGroupAdapter.setUrl(url);
		simpleGroupAdapter.setUsername(username);
		simpleGroupAdapter.setPassword(password);
		simpleGroupAdapter.setCreateUsers(GrouperLoaderConfig.getPropertyBoolean(PROP_CREATE_USERS, false));
		simpleGroupAdapter.setGroupIdAdapter(groupIdAdapter);
		simpleGroupAdapter.setDryrun(dryrun);
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

					if (group != null) {
						if (NakamuraUtils.isSimpleGroup(group) || group.getExtension().equals(MEMBER_SUFFIX)){
							simpleGroupAdapter.createGroup(group);
						}
						else {
							log.error("Received an add event for a non-simple group.");
						}
					}
					else {
						log.error("Group added event received for a null or non-simple group " + groupName);
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + groupName);
					}
					checkSupportedGroup(groupName);
					Group group = GroupFinder.findByName(getGrouperSession(), groupName, false);
					if (group == null){
						if (NakamuraUtils.isSimpleGroup(groupName)){
							simpleGroupAdapter.deleteGroup(groupName, groupName);
						}
						else {
							log.error("Received a delete event for a non-simple group.");
						}
					}
					else {
						log.error("Received a delete event for a group that still exists!");
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupId);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);

					if (member != null && "person".equals(member.getTypeName()) ){
						log.debug("Membership add, group: " + groupName + " subjectId: " + memberId);
						checkSupportedGroup(groupName);
						if (NakamuraUtils.isSimpleGroup(groupName)){
							simpleGroupAdapter.addMembership(groupId, groupName, memberId);
						}
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupId);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);

					if (member != null && "person".equals(member.getTypeName()) ){
						log.debug("Membership delete, group: " + groupName + " subjectId: " + memberId);
						checkSupportedGroup(groupName);
						if (NakamuraUtils.isSimpleGroup(groupName)){
							simpleGroupAdapter.addMembership(groupId, groupName, memberId);
						}
					}
				}
				// we successfully processed this record
			}
		}
		catch(UnsupportedGroupException e){
			log.error(e.getMessage());
		}
		catch (Exception e) {
			changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
			// we made it to this -1
			return currentId - 1;
		}
		if (currentId == -1) {
			log.error("Didn't process any records.");
			throw new RuntimeException("Couldn't process any records");
		}
		return currentId;
	}

	/**
	 * Does the group name fall inside of the stems we're configured to keep
	 * in sync with sakai?
	 * @param groupName
	 * @throws UnsupportedGroupException
	 */
	private void checkSupportedGroup(String groupName) throws UnsupportedGroupException {
		boolean supported = false;
		for (String stem: supportedStems){
			if (groupName.startsWith(stem)) {
				supported = true;
				break;
			}
		}
		if (!supported){
			throw new UnsupportedGroupException("Not configured to handle " + groupName + ". Check the elfilter.");
		}
	}
}