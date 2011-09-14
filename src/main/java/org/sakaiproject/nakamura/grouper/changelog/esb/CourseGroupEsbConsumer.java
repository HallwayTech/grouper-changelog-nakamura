package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.BaseGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseGroupNakamuraManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.TemplateGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.ChangeLogUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.misc.SaveMode;
import edu.internet2.middleware.subject.Subject;

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
 * Sakai OAE has a course named course0.
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
public class CourseGroupEsbConsumer extends BaseGroupEsbConsumer {

	private static Log log = LogFactory.getLog(CourseGroupEsbConsumer.class);

	// The interface to the Sakai OAE server.
	protected HttpCourseGroupNakamuraManagerImpl nakamuraManager;

	// Translates grouper names to Sakai OAE course group ids.
	protected GroupIdAdapterImpl groupIdAdapter;

	public static final String PROP_ADD_ADMIN_AS = "add.admin.as";
	protected String addAdminAs = "";

	private static final String ADMIN_USERNAME = "admin";

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

		String cfgPrefix = CONFIG_PREFIX + "." + consumerName + ".";
		addAdminAs = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_ADD_ADMIN_AS, "");
		log.info("addAdminAsLecturer = " + addAdminAs);

		SimpleGroupIdAdapter simpleAdapter = new SimpleGroupIdAdapter();
		simpleAdapter.loadConfiguration(consumerName);
		TemplateGroupIdAdapter templateAdapter = new TemplateGroupIdAdapter();
		templateAdapter.loadConfiguration(consumerName);

		groupIdAdapter = new GroupIdAdapterImpl();
		groupIdAdapter.loadConfiguration(consumerName);
		groupIdAdapter.setSimpleGroupIdAdapter(simpleAdapter);
		groupIdAdapter.setTemplateGroupIdAdapter(templateAdapter);

		nakamuraManager = new HttpCourseGroupNakamuraManagerImpl();
		nakamuraManager.setUrl(url);
		nakamuraManager.setUsername(username);
		nakamuraManager.setPassword(password);
		nakamuraManager.setGroupIdAdapter(groupIdAdapter);
		nakamuraManager.setCreateUsers(createUsers);
		nakamuraManager.setDryrun(dryrun);
		nakamuraManager.setPseudoGroupSuffixes(pseudoGroupSuffixes);

		nakamuraManager.setFirstNameAttribute(firstNameAttribute);
		nakamuraManager.setLastNameAttribute(lastNameAttribute);
		nakamuraManager.setEmailAttribute(emailAttribute);
		nakamuraManager.setDefaultEmailDomain(defaultEmailDomain);
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

	private void processChangeLogEntry(ChangeLogEntry entry) throws GroupModificationException, UserModificationException {

		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		String nakamuraGroupId = groupIdAdapter.getGroupId(grouperName);
		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
			processGroupAdd(grouperName, nakamuraGroupId, parentGroupId);
		}

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
			prcoessGroupDelete(grouperName, parentGroupId);
		}

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
			String subjectId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
			processMembershipAdd(grouperName, nakamuraGroupId, subjectId);
		}

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
			String subjectId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
			prcoessMembershipDelete(grouperName, nakamuraGroupId, subjectId);
		}

	}

	private void processGroupAdd(String grouperName, String nakamuraGroupId,
			String parentGroupId) throws GroupModificationException {

		log.info("START GROUP_ADD : " + grouperName);
		Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);

		if (group != null) {

			// handle the app:sakaioae:provisioned:courses:X:all group
			if (groupIdAdapter.isInstitutional(grouperName)
					&& grouperName.endsWith(":" + BaseGroupIdAdapter.ALL_GROUP_EXTENSION)){
				String applicationAllGroupName = grouperName.replaceFirst(
						groupIdAdapter.getInstitutionalCourseGroupsStem(),
						groupIdAdapter.getProvisionedCourseGroupsStem());
				// Create app:sakaoae:provisioned:course:X:all if it doesnt exist
				Group applicationAllGroup = GroupFinder.findByName(getGrouperSession(), applicationAllGroupName, false);
				if (applicationAllGroup == null){
					applicationAllGroup = Group.saveGroup(getGrouperSession(), null, null, applicationAllGroupName,
							BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true);
				}
				// Add the inst:sis:course:X:all as a member of app:sakaoae:provisioned:course:X:all
				Subject subj = SubjectFinder.findByIdOrIdentifier(grouperName, false);
				applicationAllGroup.addMember(subj, false);
				log.debug("Created " + applicationAllGroupName + " and added " + grouperName + " as a member.");
			}

			if (!nakamuraManager.groupExists(parentGroupId)){
				log.info("CREATE " + parentGroupId + " as parent of " + nakamuraGroupId);

				// Special case for the inst:sis:course:X:all
				if (groupIdAdapter.isInstitutional(grouperName)) {
					// Special handling for inst:sis courses.
					// This will provision a group in Sakai OAE when a group is created in the institutional
					// When the group is modified in Sakai OAE it will be written back to Grouper in the
					// Sakai OAE provisioned stem.
					grouperName = grouperName.replace(
							groupIdAdapter.getInstitutionalCourseGroupsStem(),
							groupIdAdapter.getProvisionedCourseGroupsStem());
				}
				// Try t get the course description from the parent stem.
				// If the parent stem has no description just use the group id.
				String description = group.getParentStem().getDescription();
				if (description == null){
					description = parentGroupId;
				}
				nakamuraManager.createGroup(grouperName, description);

				if (StringUtils.trimToNull(addAdminAs) != null){
					nakamuraManager.addMembership(parentGroupId + "-" + addAdminAs, ADMIN_USERNAME);
				}
			}
		} else {
			log.error("Group added event received for a group that doesn't exist? " + grouperName);
		}
		log.info("DONE GROUP_ADD : " + grouperName);
	}

	private void prcoessGroupDelete(String grouperName, String nakamuraGroupId) throws GroupModificationException {
		log.info("START GROUP_DELETE : " + grouperName);
		if (grouperName.endsWith(deleteRole)){
			if (nakamuraManager.groupExists(nakamuraGroupId)){
				nakamuraManager.deleteGroup(nakamuraGroupId, grouperName);
			}
			else {
				log.info(nakamuraGroupId + " does not exist.");
			}
		}
		log.info("DONE GROUP_DELETE : " + grouperName);
	}

	private void processMembershipAdd(String grouperName, String nakamuraGroupId, String subjectId) throws UserModificationException, GroupModificationException {

		Subject member = SubjectFinder.findByIdOrIdentifier(subjectId, false);
		log.info("START MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + subjectId);

		if (member != null && "person".equals(member.getTypeName())
				&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){

			if (createUsers){
				nakamuraManager.createUser(subjectId);
			}

			if (nakamuraManager.groupExists(nakamuraGroupId)) {
				nakamuraManager.addMembership(nakamuraGroupId, subjectId);
			}
			else {
				log.info(nakamuraGroupId + " does not exist. Cannot add membership");
			}

			// When a user is added to inst:sis:course:G:ROLE,
			// Remove them from app:atlas:provisioned:course:G:ROLE_excludes
			if (groupIdAdapter.isInstitutional(grouperName)){
				String excludesGroupName = grouperName.replaceFirst(
						groupIdAdapter.getInstitutionalCourseGroupsStem(),
						groupIdAdapter.getProvisionedCourseGroupsStem()) + BaseGroupIdAdapter.DEFAULT_EXCLUDES_SUFFIX;
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

	private void prcoessMembershipDelete(String grouperName,
			String nakamuraGroupId, String subjectId) throws GroupModificationException {
		Subject member = SubjectFinder.findByIdOrIdentifier(subjectId, false);
		log.info("START MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + subjectId);

		if (member != null
				&& "person".equals(member.getTypeName())
				&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){
			if (nakamuraManager.groupExists(nakamuraGroupId)) {
				nakamuraManager.deleteMembership(nakamuraGroupId, subjectId);
			}
			else {
				log.info(nakamuraGroupId + " does not exist. Cannot remove membership");
			}

			// When a user is removed from inst:sis:course:G:ROLE,
			// Remove them from app:atlas:provisioned:course:G:ROLE_includes
			if (groupIdAdapter.isInstitutional(grouperName)){
				String includesGroupName = grouperName.replaceFirst(
					groupIdAdapter.getInstitutionalCourseGroupsStem(),
					groupIdAdapter.getProvisionedCourseGroupsStem()) + BaseGroupIdAdapter.DEFAULT_INCLUDES_SUFFIX;

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

			if (allowInstitutional == false && groupIdAdapter.isInstitutional(grouperName)){
				log.info("ignoring " + sequenceNumber + " : Not processing institutional data : " + grouperName);
				ignore = true;
			}
			if (!groupIdAdapter.isCourseGroup(grouperName)){
				log.info("ignoring " + sequenceNumber + " : Not a course group : " + grouperName);
				ignore = true;
			}

			if( grouperName.endsWith(":" + BaseGroupIdAdapter.ALL_GROUP_EXTENSION)){
				if (groupIdAdapter.isInstitutional(grouperName)){
					if (!entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)){
						log.info("ignoring:  " + sequenceNumber + " all group: " + grouperName);
						ignore = true;
					}
				}
				else {
					log.info("ignoring:  " + sequenceNumber + " all group: " + grouperName);
					ignore = true;
				}
			}
		}
		return ignore;
	}

	// Used by unit tests
	public void setGroupIdAdapter(GroupIdAdapterImpl adapter){
		this.groupIdAdapter = adapter;
	}

	// Used by unit tests
	public void setGroupManager(HttpCourseGroupNakamuraManagerImpl manager){
		this.nakamuraManager = manager;
	}
}