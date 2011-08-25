package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseAdapter;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.TemplateGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.util.ChangeLogUtils;

import com.google.common.collect.MapMaker;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
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
	protected HttpCourseAdapter groupAdapter;

	// Translates grouper names to Sakai OAE course group ids.
	protected GroupIdAdapterImpl groupIdAdapter;

	// Courses already created in sakai by this object.
	protected Map<String,Boolean> coursesInSakai;

	public static final String PROP_ADD_ADMIN_LECTURER = "add.admin.as.lecturer";
	protected boolean addAdminAsLecturer = false;

	public CourseGroupEsbConsumer() {
		coursesInSakai = new MapMaker().expireAfterWrite(5, TimeUnit.MINUTES).makeMap();
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

		String cfgPrefix = CONFIG_PREFIX + "." + consumerName + ".";
		addAdminAsLecturer = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_ADD_ADMIN_LECTURER, false);
		log.info("addAdminAsLecturer = " + addAdminAsLecturer);

		SimpleGroupIdAdapter simpleAdapter = new SimpleGroupIdAdapter();
		simpleAdapter.loadConfiguration(consumerName);
		TemplateGroupIdAdapter templateAdapter = new TemplateGroupIdAdapter();
		templateAdapter.loadConfiguration(consumerName);

		groupIdAdapter = new GroupIdAdapterImpl();
		groupIdAdapter.loadConfiguration(consumerName);
		groupIdAdapter.setSimpleGroupIdAdapter(simpleAdapter);
		groupIdAdapter.setTemplateGroupIdAdapter(templateAdapter);

		groupAdapter = new HttpCourseAdapter();
		groupAdapter.setUrl(url);
		groupAdapter.setUsername(username);
		groupAdapter.setPassword(password);
		groupAdapter.setGroupIdAdapter(groupIdAdapter);
		groupAdapter.setCreateUsers(createUsers);
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
		// try catch so we can track that we made some progress
		try {
			for (ChangeLogEntry entry : changeLogEntryList) {
				currentId = entry.getSequenceNumber();
				log.info("Processing changelog entry=" + currentId);

				if (ignoreChangelogEntry(entry)){
					continue;
				}

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
					String grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);
					log.info("START GROUP_ADD : " + grouperName);

					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
					if (group == null) {
						log.error("Group added event received for a group that doesn't exist? " + grouperName);
						continue;
					}
					String nakamuraGroupId = groupIdAdapter.getGroupId(grouperName);
					String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);

					// Create the OAE Course objects when the first role group is created.
					if (!coursesInSakai.containsKey(parentGroupId) &&
							!groupAdapter.groupExists(parentGroupId)){
						log.info("CREATE " + parentGroupId + " as parent of " + nakamuraGroupId);

						// Special handling for inst:sis courses.
						// This will provision a group in Sakai OAE when a group is created in the institutional
						// When the group is modified in Sakai OAE it will be written back to Grouper in the
						// Sakai OAE provisioned stem.

						if (grouperName.startsWith(groupIdAdapter.getInstitutionalCourseGroupsStem())){
							grouperName = grouperName.replace(
									groupIdAdapter.getInstitutionalCourseGroupsStem(),
									groupIdAdapter.getProvisionedCourseGroupsStem());
						}
						groupAdapter.createGroup(grouperName, group.getParentStem().getDescription());

						if (addAdminAsLecturer){
							groupAdapter.addMembership(parentGroupId + "-lecturer", "admin");
						}
						coursesInSakai.put(parentGroupId, Boolean.TRUE);
					}

					log.info("DONE GROUP_ADD : " + grouperName);
				}

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String grouperName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);
					log.info("START GROUP_DELETE : " + grouperName);
					if (grouperName.endsWith(deleteRole)){
						groupAdapter.deleteGroup(groupIdAdapter.getGroupId(grouperName), grouperName);
					}
					log.info("DONE GROUP_DELETE : " + grouperName);
				}

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String grouperName = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
					String memberId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					Subject member = SubjectFinder.findByIdOrIdentifier(memberId, false);
					String groupId = groupIdAdapter.getGroupId(grouperName);
					log.info("START MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + memberId);

					if (member != null
							&& "person".equals(member.getTypeName())
							&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){

						groupAdapter.addMembership(groupId, memberId);
					}
					else {
						log.info("Ignoring this entry : invalid subject for membership add : " + member);
					}

					log.info("END MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + memberId);
				}

				if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String grouperName = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String memberId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdOrIdentifier(memberId, false);
					String groupId = groupIdAdapter.getGroupId(grouperName);
					log.info("START MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + memberId);

					if (member != null
							&& "person".equals(member.getTypeName())
							&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){
						groupAdapter.deleteMembership(groupId, memberId);
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
		// Stop processing changelog entries.
		catch (Exception e) {
			changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
			// we made it to this - 1
			return currentId - 1;
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
	protected boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = false;
		Long entryId = entry.getSequenceNumber();
		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		if (log.isTraceEnabled()){
			log.trace(entry.toStringDeep());
		}
		if (grouperName == null){
			log.info("ignoring: " + entryId + " Unable to get the group name from the entry. ");
			ignore = true;
		}
		else {
			if (grouperName.endsWith(":all")){
				log.info("ignoring:  " + entryId + " all group: " + grouperName);
				ignore = true;
			}
			if (allowInstitutional == false && groupIdAdapter.isInstitutional(grouperName)){
				log.info("ignoring " + entryId + " : Not processing institutional data : " + grouperName);
				ignore = true;
			}
			if (!groupIdAdapter.isCourseGroup(grouperName)){
				log.info("ignoring " + entryId + " : Not a course group : " + grouperName);
				ignore = true;
			}
		}
		return ignore;
	}

	// Used by unit tests
	public void setGroupIdAdapter(GroupIdAdapterImpl adapter){
		this.groupIdAdapter = adapter;
	}

	// Used by unit tests
	public void setGroupAdapter(HttpCourseAdapter adapter){
		this.groupAdapter = adapter;
	}
}