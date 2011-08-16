package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseAdapter;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.TemplateGroupIdAdapter;
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
 * 1. course0:students
 * 2. course0:students_includes
 * 3. course0:students_excludes
 * 4. course0:students_systemOfRecord
 * 5. course0:students_systemOfRecordAndIncludes
 *
 * Group 1 would have the effective membership: ((4 + 5) + 2) - 3.
 *
 * This class should act on flattened membership events for the first group.
 * They're called flattened since course0:students would be a composite group
 * and its membership depends on the state of the component groups (and subgroups).
 */
public class CourseGroupEsbConsumer extends BaseGroupEsbConsumer {

	private static Log log = LogFactory.getLog(CourseGroupEsbConsumer.class);

	// The interface to the SakaiOAE/nakamura server.
	protected HttpCourseAdapter groupAdapter;

	protected GroupIdAdapterImpl groupIdAdapter;

	// Courses already created in sakai by this object
	protected Set<String> coursesInSakai;

	public CourseGroupEsbConsumer() {
		coursesInSakai = new HashSet<String>();
	}

	protected void loadConfiguration(String consumerName) {
		if (configurationLoaded){
			return;
		}
		super.loadConfiguration(consumerName);

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
	}

	/**
	 * @see edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase#processChangeLogEntries(List, ChangeLogProcessorMetadata)
	 */
	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		long currentId = -1;

		String consumerName = changeLogProcessorMetadata.getConsumerName();
		loadConfiguration(consumerName);

		// try catch so we can track that we made some progress
		try {
			for (ChangeLogEntry changeLogEntry : changeLogEntryList) {
				currentId = changeLogEntry.getSequenceNumber();
				log.debug("Processing changelog entry=" + currentId);

				if (ignoreChangelogEntry(changeLogEntry)){
					continue;
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);
					log.info("START GROUP_ADD : " + grouperName);

					// Use a Mutable group so we can rewrite the name before we send it to OAE
					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
					if (group == null) {
						log.error("Group added event received for a group that doesn't exist? " + grouperName);
						continue;
					}
					String nakamuraGroupId = groupIdAdapter.getGroupId(grouperName);
					String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);

					// Create the OAE Course objects when the first role group is created.
					if (!coursesInSakai.contains(parentGroupId) &&
							!groupAdapter.groupExists(parentGroupId)){
						log.debug("CREATE " + parentGroupId + " as parent of " + nakamuraGroupId);

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
						coursesInSakai.add(parentGroupId);
					}

					log.info("DONE GROUP_ADD : " + grouperName);
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);
					log.info("START GROUP_DELETE : " + grouperName);

					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
					if (group == null && grouperName.endsWith(deleteRole)){
						groupAdapter.deleteGroup(groupIdAdapter.getGroupId(grouperName), grouperName);
					}
					else {
						log.error("Received a delete event for a group that still exists!");
					}
					log.info("DONE GROUP_DELETE : " + grouperName);
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);
					String groupId = groupIdAdapter.getGroupId(grouperName);
					log.info("START MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + memberId);

					if (!groupIdAdapter.isIncludeExcludeSubGroup(grouperName) && member != null && "person".equals(member.getTypeName()) ){
						groupAdapter.addMembership(groupId, memberId);
					}

					log.info("END MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + memberId);
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);
					String groupId = groupIdAdapter.getGroupId(grouperName);
					log.info("START MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + memberId);

					if (!groupIdAdapter.isIncludeExcludeSubGroup(grouperName) && member != null && "person".equals(member.getTypeName()) ){
						groupAdapter.deleteMembership(groupId, memberId);
					}

					log.info("END MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + memberId);
				}
				// we successfully processed this record
			}
		}
		// Stop processing changelog entries.
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

	protected boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = false;
		Long entryId = entry.getSequenceNumber();
		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		if (log.isTraceEnabled()){
			log.trace(entry.toStringDeep());
		}
		if (grouperName == null){
			log.debug("ignoring: " + entryId + " Unable to get the group name from the entry. ");
			ignore = true;
		}
		else {
			if (grouperName.endsWith(":all")){
				log.debug("ignoring:  " + entryId + " all group: " + grouperName);
				ignore = true;
			}
			if (groupIdAdapter.isInstitutional(grouperName) && allowInstitutional == false){
				log.debug("ignoring " + entryId + " : Not processing institutional data : " + grouperName);
				ignore = true;
			}
			if (!groupIdAdapter.isCourseGroup(grouperName)){
				log.debug("ignoring " + entryId + " : Not a course group : " + grouperName);
				ignore = true;
			}
		}
		return ignore;
	}

	public void setGroupIdAdapter(GroupIdAdapterImpl adapter){
		this.groupIdAdapter = adapter;
	}

	public void setGroupAdapter(HttpCourseAdapter adapter){
		this.groupAdapter = adapter;
	}
}