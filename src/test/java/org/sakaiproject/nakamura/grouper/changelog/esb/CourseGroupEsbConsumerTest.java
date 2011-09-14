package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.support.membermodification.MemberMatcher.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;
import junit.framework.TestCase;

import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sakaiproject.nakamura.grouper.changelog.BaseGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseGroupNakamuraManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;

import com.google.common.collect.ImmutableList;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.misc.SaveMode;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { GrouperUtil.class, GroupFinder.class, GrouperSession.class, SubjectFinder.class, Group.class })
public class CourseGroupEsbConsumerTest extends TestCase {

	private CourseGroupEsbConsumer consumer;
	private HttpCourseGroupNakamuraManagerImpl nakamuraManager;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	private static final long SEQUENCE_NUMBER = 25;

	private Group group;
	private Stem stem;
	private GrouperSession session;

	private static final String PARENT_DESCRIPTION = "parent description";

	private String course1LecturersApplicationGroupName   = "edu:apps:sakaiaoe:courses:course1:lecturers";
	private String course1StudentsApplicationGroupName   = "edu:apps:sakaiaoe:courses:course1:students";
	private String course1StudentsInstitutionalGroupName = "inst:sis:courses:course1:students";
	private String course1AllApplicationGroupName        = "edu:apps:sakaiaoe:courses:course1:all";
	private String course1AllInstitutionalGroupName      = "inst:sis:courses:course1:all";

	private String courseGroupId = "course1";
	private String courseStudentGroupId = "course1-student";
	private String courseLecturerGroupId = "course1-lecturer";

	private String invalidCourseGroupName = "edu:apps:sakaioae:simplegroups:x:managers";

	public void setUp(){

		suppress(method(GrouperUtil.class, "getLog"));
		// Static classes
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		mockStatic(SubjectFinder.class);

		// Shared Grouper objects
		stem = mock(Stem.class);
		when(stem.getDescription()).thenReturn(PARENT_DESCRIPTION);
		group = mock(Group.class);
		when(group.getParentStem()).thenReturn(stem);

		session = mock(GrouperSession.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		entry = mock(ChangeLogEntry.class);
		when(entry.getSequenceNumber()).thenReturn(SEQUENCE_NUMBER);

		nakamuraManager = mock(HttpCourseGroupNakamuraManagerImpl.class);

		groupIdAdapter = mock(GroupIdAdapterImpl.class);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("inst:sis:courses");
		when(groupIdAdapter.getProvisionedCourseGroupsStem()).thenReturn("edu:apps:sakaiaoe:courses");

		when(groupIdAdapter.isCourseGroup(course1LecturersApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1StudentsApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1StudentsInstitutionalGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1AllApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1AllInstitutionalGroupName)).thenReturn(true);

		when(groupIdAdapter.isInstitutional(course1StudentsApplicationGroupName)).thenReturn(false);
		when(groupIdAdapter.isInstitutional(course1StudentsInstitutionalGroupName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(course1AllApplicationGroupName)).thenReturn(false);
		when(groupIdAdapter.isInstitutional(course1AllInstitutionalGroupName)).thenReturn(true);

		when(groupIdAdapter.isCourseGroup(invalidCourseGroupName)).thenReturn(false);

		when(groupIdAdapter.getPseudoGroupParent(courseStudentGroupId)).thenReturn(courseGroupId);
		when(groupIdAdapter.getPseudoGroupParent(courseLecturerGroupId)).thenReturn(courseGroupId);

		when(groupIdAdapter.getGroupId(course1StudentsApplicationGroupName)).thenReturn(courseStudentGroupId);

		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		consumer = new CourseGroupEsbConsumer();
		consumer.setGroupManager(nakamuraManager);
		consumer.setGroupIdAdapter(groupIdAdapter);
		consumer.setConfigurationLoaded(true);
		consumer.setPseudoGroupSuffixes("student, manager, member, ta, lecturer");
		consumer.setDeleteRole("students");
	}

	public void testIgnoreInvalidEntryType() throws Exception{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(false);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(false);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_UPDATE)).thenReturn(false);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)).thenReturn(false);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)).thenReturn(false);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreAll(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName)).thenReturn(course1AllApplicationGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));

		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName)).thenReturn(course1AllInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnoreAddAll(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1AllInstitutionalGroupName);
		when(groupIdAdapter.isInstitutional(course1AllInstitutionalGroupName)).thenReturn(true);
		consumer.allowInstitutional = true;
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotACourseGroup(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(invalidCourseGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsApplicationGroupName);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontAllowProvisionedByDefault(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testAllowProvisioned(){
		consumer.setAllowInstitutional(true);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsInstitutionalGroupName);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testAddGroup() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(entry));

		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn("some_course-student");
		when(groupIdAdapter.getPseudoGroupParent("some_course-student")).thenReturn("some_course");
		when(nakamuraManager.groupExists("some_course")).thenReturn(false);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).createGroup(grouperName, "parent description");
	}

	public void testAddGroupDefaultTitle() throws GroupModificationException{

		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsApplicationGroupName);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");
		when(stem.getDescription()).thenReturn(null);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(false);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).createGroup(course1StudentsApplicationGroupName, courseGroupId);
	}

	public void testAddGroupInstitutional() throws GroupModificationException{
		consumer.setAllowInstitutional(true);
		String grouperName = "inst:sis:courses:some:course:students";
		String rewrittenGrouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn("some_course-student");
		when(groupIdAdapter.getPseudoGroupParent("some_course-student")).thenReturn("some_course");
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);
		when(nakamuraManager.groupExists("some_course")).thenReturn(false);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).createGroup(rewrittenGrouperName, "parent description");
	}

	public void testAddInstitutionalAllGroup() throws GroupModificationException{
		mockStatic(Group.class);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1AllInstitutionalGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(false);

		Group appAllGroup = mock(Group.class);
		Group instAllGroup = mock(Group.class);
		when(GroupFinder.findByName(session, course1AllApplicationGroupName, false)).thenReturn(null);
		when(GroupFinder.findByName(session, course1AllInstitutionalGroupName, false)).thenReturn(instAllGroup);

		when(Group.saveGroup(session, null, null, course1AllApplicationGroupName, BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true)).thenReturn(appAllGroup);
		Subject instAllGroupSubject = mock(Subject.class);
		when(SubjectFinder.findByIdOrIdentifier(course1AllInstitutionalGroupName, false)).thenReturn(instAllGroupSubject);

		consumer.allowInstitutional = true;
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(appAllGroup).addMember(instAllGroupSubject);
		verifyStatic();
		Group.saveGroup(session, null, null, course1AllApplicationGroupName, BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true);
	}

	public void testDeleteGroupIsNotNull() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(course1StudentsApplicationGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(true);
		when(groupIdAdapter.isIncludeExcludeSubGroup(course1StudentsApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).groupExists(courseGroupId);
		verify(nakamuraManager).deleteGroup(courseGroupId, course1StudentsApplicationGroupName);
	}

	public void testDeleteGroup() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(course1StudentsApplicationGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(true);
		when(groupIdAdapter.isIncludeExcludeSubGroup(course1StudentsApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(null);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).groupExists(courseGroupId);
		verify(nakamuraManager).deleteGroup(courseGroupId, course1StudentsApplicationGroupName);
	}

	public void testAddAdminAsLecturer() throws GroupModificationException, UserModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1LecturersApplicationGroupName);
		when(groupIdAdapter.isInstitutional(course1LecturersApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1LecturersApplicationGroupName, false)).thenReturn(group);

		when(groupIdAdapter.getGroupId(course1LecturersApplicationGroupName)).thenReturn(courseLecturerGroupId);
		when(nakamuraManager.groupExists(courseLecturerGroupId)).thenReturn(false);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		consumer.addAdminAs = "lecturer";
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).createGroup(course1LecturersApplicationGroupName, "parent description");
		verify(nakamuraManager).addMembership(courseLecturerGroupId, "admin");
	}
}
