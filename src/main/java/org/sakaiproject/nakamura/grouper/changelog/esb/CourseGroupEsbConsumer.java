package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;

import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseAdapter;
import org.sakaiproject.nakamura.grouper.changelog.TemplateGroupIdAdapter;
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

	private static Log log = GrouperUtil.getLog(CourseGroupEsbConsumer.class);

	// The interface to the SakaiOAE/nakamura server.
	private HttpCourseAdapter groupAdapter;

	protected TemplateGroupIdAdapter templateGroupIdAdapter;

	public CourseGroupEsbConsumer() {
		log.info("HELLO");
	}

	protected void loadConfiguration(String consumerName) {
		super.loadConfiguration(consumerName);

		templateGroupIdAdapter = new TemplateGroupIdAdapter();
		templateGroupIdAdapter.loadConfiguration(consumerName);
		templateGroupIdAdapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		templateGroupIdAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		templateGroupIdAdapter.setAdhocStem(adhocStem);
		templateGroupIdAdapter.setProvisionedStem(provisionedStem);

		groupAdapter = new HttpCourseAdapter();
		groupAdapter.setUrl(url);
		groupAdapter.setUsername(username);
		groupAdapter.setPassword(password);
		groupAdapter.setGroupIdAdapter(templateGroupIdAdapter);
		groupAdapter.setCreateUsers(createUsers);
		groupAdapter.setDryrun(dryrun);
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

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);
					log.info(ChangeLogTypeBuiltin.GROUP_ADD + ": name=" + grouperName);
					if (isSupportedGroup(grouperName)) {
						Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);

						if (group != null){
							if (NakamuraUtils.isCourseGroup(group)){
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
							log.error("Group added event received for a group that doesn't exist? " + grouperName);
						}
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);
					log.info(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + grouperName);
					if (isSupportedGroup(grouperName)) {
						Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
						if (group == null || NakamuraUtils.isCourseGroup(grouperName)){
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
}