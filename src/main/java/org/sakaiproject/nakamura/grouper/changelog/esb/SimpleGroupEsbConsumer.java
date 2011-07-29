
package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;

import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.HttpSimpleGroupAdapter;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GroupType;
import edu.internet2.middleware.grouper.SubjectFinder;
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
	private HttpSimpleGroupAdapter groupAdapter;

	public static final String MANAGER_SUFFIX = "manager";
	public static final String MEMBER_SUFFIX = "member";

	protected void loadConfiguration(String consumerName) {
		super.loadConfiguration(consumerName);

		SimpleGroupIdAdapter groupIdAdapter = new SimpleGroupIdAdapter();
		groupIdAdapter.setProvisionedSimpleGroupsStem(provisionedStem);
		groupIdAdapter.setAdhocSimpleGroupsStem(adhocStem);
		groupIdAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		groupIdAdapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);

		groupAdapter = new HttpSimpleGroupAdapter();
		groupAdapter.setUrl(url);
		groupAdapter.setUsername(username);
		groupAdapter.setPassword(password);
		groupAdapter.setCreateUsers(createUsers);
		groupAdapter.setGroupIdAdapter(groupIdAdapter);
		groupAdapter.setDryrun(dryrun);
	}

	/**
	 * @see edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase#processChangeLogEntries(List, ChangeLogProcessorMetadata)
	 */
	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		String consumerName = changeLogProcessorMetadata.getConsumerName();
		loadConfiguration(consumerName);

		long currentId = -1;

		try {
			// try catch so we can track that we made some progress
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
					if (isSupportedGroup(groupName)) {
						Group group = GroupFinder.findByName(getGrouperSession(), groupName, false);

						if (group != null) {
							if (NakamuraUtils.isSimpleGroup(group)){
								for (GroupType groupType: group.getTypes()){
									// Create the OAE Course objects when the student_systemOfRecord group is created.
									// That group has the group type addIncludeExclude
									if (groupType.getName().equals(ADD_INCLUDE_EXCLUDE) &&
											group.getExtension().equals(createDeleteRole + DEFAULT_SYSTEM_OF_RECORD_SUFFIX)){
										groupAdapter.createGroup(group);
									}
								}
							}
						}
						else {
							log.error("Group added event received for a null or non-simple group " + groupName);
						}
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);
					log.info(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + grouperName);
					if (isSupportedGroup(grouperName)) {
						Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
						if (group == null || NakamuraUtils.isSimpleGroup(grouperName)){
							if (grouperName.endsWith(createDeleteRole + DEFAULT_SYSTEM_OF_RECORD_SUFFIX)){
								groupAdapter.deleteGroup(grouperName, grouperName);
							}
						}
						else {
							log.error("Received a delete event for a group that still exists!");
						}
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupId);
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);

					if (isSupportedGroup(grouperName)) {
						if (!isIncludeExcludeSubGroup(grouperName) && member != null && "person".equals(member.getTypeName()) ){
							log.info("Membership add, group: " + grouperName + " subjectId: " + memberId);

							if (NakamuraUtils.isCourseGroup(grouperName)){
								groupAdapter.addMembership(groupId, grouperName, memberId);
							}
						}
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupId);
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);

					if (isSupportedGroup(grouperName)) {
						if (!isIncludeExcludeSubGroup(grouperName) && member != null && "person".equals(member.getTypeName()) ){
							log.info("Membership delete, group: " + grouperName + " subjectId: " + memberId);
							if (NakamuraUtils.isCourseGroup(grouperName)){
								groupAdapter.deleteMembership(groupId, grouperName, memberId);
							}
						}
					}
				}
				// we successfully processed this record
			}
		}
		catch (Exception e) {
			changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
			// we made it to currentId - 1
			if (currentId != -1){
				currentId--;
			}
		}

		if (currentId == -1) {
			log.error("Didn't process any records.");
			throw new RuntimeException("Couldn't process any records");
		}

		return currentId;
	}
}