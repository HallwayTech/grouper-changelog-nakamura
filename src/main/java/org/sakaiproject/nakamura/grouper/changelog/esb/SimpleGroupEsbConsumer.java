package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.BaseGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpSimpleGroupNakamuraManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
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
	private HttpSimpleGroupNakamuraManagerImpl nakamuraManager;

	// Translates grouper names to Sakai OAE course group ids.
	private GroupIdAdapterImpl groupIdAdapter;

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
		groupIdAdapter = new GroupIdAdapterImpl();
		groupIdAdapter.loadConfiguration(consumerName);
		groupIdAdapter.setSimpleGroupIdAdapter(simpleAdapter);

		nakamuraManager = new HttpSimpleGroupNakamuraManagerImpl();
		nakamuraManager.setUrl(url);
		nakamuraManager.setUsername(username);
		nakamuraManager.setPassword(password);
		nakamuraManager.setCreateUsers(createUsers);
		nakamuraManager.setGroupIdAdapter(groupIdAdapter);
		nakamuraManager.setDryrun(dryrun);
		nakamuraManager.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		nakamuraManager.setDefaultEmailDomain(defaultEmailDomain);

		nakamuraManager.setFirstNameAttribute(firstNameAttribute);
		nakamuraManager.setLastNameAttribute(lastNameAttribute);
		nakamuraManager.setEmailAttribute(emailAttribute);
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

				if (! ignoreChangelogEntry(entry)){
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

	private void processChangeLogEntry(ChangeLogEntry entry) throws IllegalStateException, GroupModificationException {

		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		String nakamuraGroupId = groupIdAdapter.getGroupId(grouperName);
		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
			processGroupAdd(grouperName, nakamuraGroupId, parentGroupId);
		}
		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
			processGroupDelete(grouperName, parentGroupId);
		}

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
			String subjectId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
			processMembershipAdd(grouperName, nakamuraGroupId, subjectId);
		}

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
			String subjectId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
			processMembershipDelete(grouperName, nakamuraGroupId, subjectId);
		}
	}

	private void processGroupAdd(String grouperName, String nakamuraGroupId,
			String parentGroupId) throws IllegalStateException, GroupModificationException {
		if (log.isDebugEnabled()){
			log.debug(ChangeLogTypeBuiltin.GROUP_ADD + ": name=" + grouperName);
		}
		Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);

		if (group != null && !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)) {

			// Special case for the inst:sis:course:X:all
			if (groupIdAdapter.isInstitutional(grouperName)
					&& grouperName.endsWith(":" + BaseGroupIdAdapter.ALL_GROUP_EXTENSION)){
				// Check if app:sakaioae:provisioned:course:X:all exists
				String appGrouperName = grouperName.replaceFirst(
						groupIdAdapter.getInstitutionalSimpleGroupsStem(),
						groupIdAdapter.getProvisionedSimpleGroupsStem());
				Group appAllGroup = GroupFinder.findByName(getGrouperSession(), appGrouperName, false);
				// Add the inst:sis:course:X:all as a member of app:sakaoae:provisioned:course:X:all
				if (appAllGroup != null){
					appAllGroup.addMember((Subject) GroupFinder.findByName(getGrouperSession(), grouperName, false));
				}
			}
			// Create the OAE Course objects when the first role group is created.
			else if (!nakamuraManager.groupExists(parentGroupId)){

				log.debug("CREATE" + parentGroupId + " as parent of " + nakamuraGroupId);

				if (grouperName.startsWith(groupIdAdapter.getInstitutionalCourseGroupsStem())){
					grouperName = grouperName.replace(
							groupIdAdapter.getInstitutionalCourseGroupsStem(),
							groupIdAdapter.getProvisionedCourseGroupsStem());
				}

				nakamuraManager.createGroup(grouperName, group.getParentStem().getDescription());
				log.info("DONE with the GROUP_ADD event for " + grouperName);
			}
		}
		else {
			log.error("Group added event received for a null or non-simple group " + grouperName);
		}

	}

	private void processGroupDelete(String grouperName, String nakamuraGroupId) throws GroupModificationException {
		log.info(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + grouperName);
		Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
		if (group == null && grouperName.endsWith(deleteRole)){
			if (nakamuraManager.groupExists(nakamuraGroupId)){
				nakamuraManager.deleteGroup(nakamuraGroupId, grouperName);
			}
			else {
				log.error(nakamuraGroupId  + " Does not exist. Not deleting.");
			}
		}
		else {
			log.error("Received a delete event for a group that still exists!");
		}
	}

	private void processMembershipAdd(String grouperName,
			String nakamuraGroupId, String subjectId) throws GroupModificationException {
		Subject member = SubjectFinder.findByIdOrIdentifier(subjectId, false);
		log.info("START MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + subjectId);

		if (member != null && "person".equals(member.getTypeName())) {

			if (!groupIdAdapter.isIncludeExcludeSubGroup(grouperName)
					&& nakamuraManager.groupExists(nakamuraGroupId)) {
				nakamuraManager.addMembership(nakamuraGroupId, subjectId);
			}
			else {
				log.info(nakamuraGroupId + " does not exist. Cannot add membership");
			}

			// When a user is added to inst:sis:course:G:ROLE,
			// Remove them from app:atlas:provisioned:course:G:ROLE_excludes
			if (groupIdAdapter.isInstitutional(grouperName)){
				String excludesGroupName = grouperName.replaceFirst(
						groupIdAdapter.getInstitutionalSimpleGroupsStem(),
						groupIdAdapter.getProvisionedSimpleGroupsStem()) + BaseGroupIdAdapter.DEFAULT_EXCLUDES_SUFFIX;
				Group excludesGroup = GroupFinder.findByName(getGrouperSession(), excludesGroupName, false);
				if (excludesGroup != null && excludesGroup.hasMember(member)){
					excludesGroup.deleteMember(member);
				}
			}
		}
		else {
			log.info("Ignoring this entry : invalid subject for membership add : " + member);
		}

		log.info("END MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + subjectId);
	}

	private void processMembershipDelete(String grouperName,
			String nakamuraGroupId, String subjectId) throws GroupModificationException {
		Subject member = SubjectFinder.findByIdOrIdentifier(subjectId, false);
		log.info("START MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + subjectId);

		if (member != null && "person".equals(member.getTypeName())){

			if (!groupIdAdapter.isIncludeExcludeSubGroup(grouperName)
					&& nakamuraManager.groupExists(nakamuraGroupId)) {
				nakamuraManager.deleteMembership(nakamuraGroupId, subjectId);
			}
			else {
				log.info(nakamuraGroupId + " does not exist. Cannot remove membership");
			}

			// When a user is removed from inst:sis:course:G:ROLE,
			// Remove them from app:atlas:provisioned:course:G:ROLE_includes
			if (groupIdAdapter.isInstitutional(grouperName)){
				String includesGroupName = grouperName.replaceFirst(
					groupIdAdapter.getInstitutionalSimpleGroupsStem(),
					groupIdAdapter.getProvisionedSimpleGroupsStem()) + BaseGroupIdAdapter.DEFAULT_INCLUDES_SUFFIX;

				Group includesGroup = GroupFinder.findByName(getGrouperSession(), includesGroupName, false);
				if (includesGroup != null && includesGroup.hasMember(member)){
					includesGroup.deleteMember(member);
				}
			}
		}
		else {
			log.info("Ignoring this entry : invalid subject for membership delete : " + member);
		}
		log.info("END MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + subjectId);
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
			if(grouperName.endsWith(":all")
					&& groupIdAdapter.isInstitutional(grouperName)
					&& entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD) == false){
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
	public void setGroupManager(HttpSimpleGroupNakamuraManagerImpl manager) {
		this.nakamuraManager = manager;
	}

	// Used by unit tests
	public void setGroupIdAdapter(GroupIdAdapterImpl groupIdAdapter) {
		this.groupIdAdapter = groupIdAdapter;
	}
}