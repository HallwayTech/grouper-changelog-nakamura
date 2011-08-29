package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpSimpleGroupAdapter;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.util.ChangeLogUtils;

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

	// Translates grouper names to Sakai OAE course group ids.
	private GroupIdAdapterImpl groupIdAdapter;

	private Set<String> simpleGroupsInSakai;

	public static final String MANAGER_SUFFIX = "manager";
	public static final String MEMBER_SUFFIX = "member";

	public SimpleGroupEsbConsumer(){
		simpleGroupsInSakai = new HashSet<String>();
	}

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
		groupIdAdapter = new GroupIdAdapterImpl();
		groupIdAdapter.loadConfiguration(consumerName);
		groupIdAdapter.setSimpleGroupIdAdapter(simpleAdapter);

		groupAdapter = new HttpSimpleGroupAdapter();
		groupAdapter.setUrl(url);
		groupAdapter.setUsername(username);
		groupAdapter.setPassword(password);
		groupAdapter.setCreateUsers(createUsers);
		groupAdapter.setGroupIdAdapter(groupIdAdapter);
		groupAdapter.setDryrun(dryrun);
		groupAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);

		groupAdapter.setFirstNameAttribute(firstNameAttribute);
		groupAdapter.setLastNameAttribute(lastNameAttribute);
		groupAdapter.setEmailAttribute(emailAttribute);
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

				if (log.isDebugEnabled()){
					log.info("Processing changelog entry=" + currentId);
				}

				if (ignoreChangelogEntry(entry)){
					continue;
				}

				String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
				String nakamuraGroupId = groupIdAdapter.getGroupId(grouperName);
				String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_ADD + ": name=" + grouperName);
					}
					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);

					if (group != null) {
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

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					log.info(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + grouperName);
					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
					if (group == null && grouperName.endsWith(deleteRole)){
						if (groupAdapter.groupExists(nakamuraGroupId)){
							groupAdapter.deleteGroup(groupIdAdapter.getGroupId(grouperName), grouperName);
						}
						else {
							log.error(nakamuraGroupId  + " Does not exist. Not deleting.");
						}
					}
					else {
						log.error("Received a delete event for a group that still exists!");
					}
				}

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String memberId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					Subject member = SubjectFinder.findByIdOrIdentifier(memberId, false);
					log.info("START MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + memberId);

					if (member != null && "person".equals(member.getTypeName())
							&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){

						if (groupAdapter.groupExists(nakamuraGroupId)) {
							groupAdapter.addMembership(nakamuraGroupId, memberId);
						}
						else {
							log.info(nakamuraGroupId + " does not exist. Cannot add membership");
						}
					}
					else {
						log.info("Ignoring this entry : invalid subject for membership add : " + member);
					}

					log.info("END MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + memberId);
				}

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String memberId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdOrIdentifier(memberId, false);
					log.info("START MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + memberId);

					if (member != null
							&& "person".equals(member.getTypeName())
							&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){
						if (groupAdapter.groupExists(nakamuraGroupId)) {
							groupAdapter.deleteMembership(nakamuraGroupId, memberId);
						}
						else {
							log.info(nakamuraGroupId + " does not exist. Cannot remove membership");
						}
					}
					else {
						log.info("Ignoring this entry : invalid subject for membership delete : " + member);
					}

					log.info("END MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + memberId);
				}

				log.info("Finished the batch of " + entryCount + " entries : " +
						changeLogEntryList.get(0).getSequenceNumber() + " - " +
						changeLogEntryList.get(entryCount - 1).getSequenceNumber());
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

	/**
	 * @param entry a change log entry
	 * @return whether or not to ignore this entry
	 */
	public boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = false;
		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		if (grouperName == null){
			log.debug("ignoring: Unable to get the group name from the entry : " + entry.toStringDeep());
			ignore = true;
		}
		else {
			if(grouperName.endsWith(":all")){
				log.debug("ignoring: all group: " + grouperName);
				ignore = true;
			}
			if (allowInstitutional == false && groupIdAdapter.isInstitutional(grouperName)){
				log.debug("ignoring: Not processing institutional data : " + grouperName);
				ignore = true;
			}

			if (!groupIdAdapter.isSimpleGroup(grouperName)){
				log.debug("ignoring: Not a simple group : " + grouperName);
				ignore = true;
			}
		}
		return ignore;
	}

	// Used by unit tests
	public void setGroupAdapter(HttpSimpleGroupAdapter groupAdapter) {
		this.groupAdapter = groupAdapter;
	}

	// Used by unit tests
	public void setGroupIdAdapter(GroupIdAdapterImpl groupIdAdapter) {
		this.groupIdAdapter = groupIdAdapter;
	}
}