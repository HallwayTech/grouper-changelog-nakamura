package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpSimpleGroupAdapter;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.subject.Subject;

/**
 * Provision Simple Groups and memberships to Sakai OAE.
 */
public class SimpleGroupEsbConsumer extends BaseGroupEsbConsumer {

	private static Log log = LogFactory.getLog(SimpleGroupEsbConsumer.class);

	// The interface to the SakaiOAE/nakamura server.
	private HttpSimpleGroupAdapter groupAdapter;

	private GroupIdAdapterImpl groupIdAdapter;

	private Set<String> simpleGroupsInSakai;

	public static final String MANAGER_SUFFIX = "manager";
	public static final String MEMBER_SUFFIX = "member";

	public SimpleGroupEsbConsumer(){
		simpleGroupsInSakai = new HashSet<String>();
	}

	protected void loadConfiguration(String consumerName) {
		if (configurationLoaded){
			return;
		}
		super.loadConfiguration(consumerName);

		groupIdAdapter = new GroupIdAdapterImpl();

		groupAdapter = new HttpSimpleGroupAdapter();
		groupAdapter.setUrl(url);
		groupAdapter.setUsername(username);
		groupAdapter.setPassword(password);
		groupAdapter.setCreateUsers(createUsers);
		groupAdapter.setGroupIdAdapter(groupIdAdapter);
		groupAdapter.setDryrun(dryrun);
		groupAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
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

				if (ignoreChangelogEntry(changeLogEntry)){
					continue;
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_ADD + ": name=" + grouperName);
					}
					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);

					if (group != null) {
						String nakamuraGroupId = groupIdAdapter.getGroupId(grouperName);
						String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
						// Create the OAE Course objects when the first role group is created.
						if (!simpleGroupsInSakai.contains(parentGroupId) &&
								!groupAdapter.groupExists(parentGroupId)){

							log.debug("CREATE" + parentGroupId + " as parent of " + nakamuraGroupId);

							if (grouperName.startsWith(groupIdAdapter.getInstitutionalCourseGroupsStem())){
								grouperName = grouperName.replace(
										groupIdAdapter.getInstitutionalCourseGroupsStem(),
										groupIdAdapter.getProvisionedCourseGroupsStem());
							}

							groupAdapter.createGroup(grouperName, group.getParentStem().getDescription());
							simpleGroupsInSakai.add(parentGroupId);
							log.info("DONE with the GROUP_ADD event for " + grouperName);
						}
					}
					else {
						log.error("Group added event received for a null or non-simple group " + grouperName);
					}

				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);
					log.info(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + grouperName);
					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
					if (group == null){
						if (grouperName.endsWith(deleteRole)){
							groupAdapter.deleteGroup(groupIdAdapter.getGroupId(grouperName), grouperName);
						}
					}
					else {
						log.error("Received a delete event for a group that still exists!");
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);
					String groupId = groupIdAdapter.getGroupId(grouperName);

					if (!groupIdAdapter.isIncludeExcludeSubGroup(grouperName) && member != null && "person".equals(member.getTypeName()) ){
						log.info("Membership add, group: " + grouperName + " subjectId: " + memberId);
						groupAdapter.addMembership(groupId, memberId);
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);
					String groupId = groupIdAdapter.getGroupId(grouperName);

					if (!groupIdAdapter.isIncludeExcludeSubGroup(grouperName) && member != null && "person".equals(member.getTypeName()) ){
						log.info("Membership delete, group: " + grouperName + " subjectId: " + memberId);
						groupAdapter.deleteMembership(groupId, memberId);
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

	public boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = false;
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

		if (grouperName == null){
			log.debug("ignoring: Unable to get the group name from the entry : " + entry.toStringDeep());
			ignore = true;
		}

		if (grouperName != null && grouperName.endsWith(":all")){
			log.debug("ignoring: all group: " + grouperName);
			ignore = true;
		}

		if (groupIdAdapter.isInstitutional(grouperName) && allowInstitutional == false){
			log.debug("ignoring: Not processing institutional data : " + grouperName);
			ignore = true;
		}

		if (!groupIdAdapter.isSimpleGroup(grouperName)){
			log.debug("ignoring: Not a simple group : " + grouperName);
			ignore = true;
		}
		return ignore;
	}

	public void setGroupAdapter(HttpSimpleGroupAdapter groupAdapter) {
		this.groupAdapter = groupAdapter;
	}

	public void setGroupIdAdapter(GroupIdAdapterImpl groupIdAdapter) {
		this.groupIdAdapter = groupIdAdapter;
	}
}