package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UnsupportedGroupException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GroupType;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
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
	private HttpCourseAdapter courseGroupAdapter;

	// This job will try to process events for groups in these stems
	private Set<String> supportedStems;

	private static final String ADD_INCLUDE_EXCLUDE = "addIncludeExclude";

	// Decides where we accept events from
	public static final String PROP_ADHOC_COURSES_STEM =  PROPERTY_KEY_PREFIX + ".courses.adhoc.stem";
	public static final String PROP_PROVISIONED_COURSES_STEM =  PROPERTY_KEY_PREFIX + ".courses.provisioned.stem";

	public static final String PROP_ADHOC_SIMPLE_STEM =  PROPERTY_KEY_PREFIX + ".simplegroups.adhoc.stem";
	public static final String PROP_PROVISIONED_SIMPLE_STEM =  PROPERTY_KEY_PREFIX + ".simplegroups.provisioned.stem";

	private static final String CREATE_DELETE_ROLE = "student";

	public CourseGroupEsbConsumer() throws MalformedURLException {
		super();

		supportedStems = new HashSet<String>();
		supportedStems.add(GrouperLoaderConfig.getPropertyString(PROP_ADHOC_COURSES_STEM, true));
		supportedStems.add(GrouperLoaderConfig.getPropertyString(PROP_PROVISIONED_COURSES_STEM, true));

		courseGroupAdapter = new HttpCourseAdapter();
		courseGroupAdapter.setUrl(url);
		courseGroupAdapter.setUsername(username);
		courseGroupAdapter.setPassword(password);
		courseGroupAdapter.setGroupIdAdapter(templateGroupIdAdapter);
		courseGroupAdapter.setCreateUsers(GrouperLoaderConfig.getPropertyBoolean(PROP_CREATE_USERS, false));
		courseGroupAdapter.setDryrun(dryrun);
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

					if (group != null){
						if (NakamuraUtils.isCourseGroup(group)){
							for (GroupType groupType: group.getTypes()){
								// Create the OAE Course objects when the student_systemOfRecord group is created.
								// That group has the group type addIncludeExclude
								if (groupType.getName().equals(ADD_INCLUDE_EXCLUDE) &&
										group.getExtension().equals(CREATE_DELETE_ROLE + DEFAULT_SYSTEM_OF_RECORD_SUFFIX)){
									courseGroupAdapter.createGroup(group);
								}
							}
						}
					}
					else {
						log.error("Group added event received for a group that doesn't exist? " + groupName);
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String grouperName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + grouperName);
					}
					checkSupportedGroup(grouperName);
					Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
					if (group == null || NakamuraUtils.isCourseGroup(grouperName)){
						if (grouperName.endsWith(CREATE_DELETE_ROLE + DEFAULT_SYSTEM_OF_RECORD_SUFFIX)){
							courseGroupAdapter.deleteGroup(grouperName, grouperName);
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

					if (!isIncludeExcludeSubGroup(groupName) && member != null && "person".equals(member.getTypeName()) ){
						log.debug("Membership add, group: " + groupName + " subjectId: " + memberId);
						checkSupportedGroup(groupName);

						if (NakamuraUtils.isCourseGroup(groupName)){
							courseGroupAdapter.addMembership(groupId, groupName, memberId);
						}
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupId);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);

					if (!isIncludeExcludeSubGroup(groupName) && member != null && "person".equals(member.getTypeName()) ){
						log.debug("Membership delete, group: " + groupName + " subjectId: " + memberId);
						checkSupportedGroup(groupName);

						if (NakamuraUtils.isCourseGroup(groupName)){
							courseGroupAdapter.deleteMembership(groupId, groupName, memberId);
						}
					}
				}
				// we successfully processed this record
			}
		}
		catch(UnsupportedGroupException e){
			log.error(e.getMessage());
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

	/**
	 * Is this a sub group of the addIncludeExclude group?
	 * @param grouperName
	 * @return
	 */
	private boolean isIncludeExcludeSubGroup(String grouperName){
		for (String suffix: includeExcludeSuffixes){
			if (grouperName.endsWith(suffix)){
				return true;
			}
		}
		return false;
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