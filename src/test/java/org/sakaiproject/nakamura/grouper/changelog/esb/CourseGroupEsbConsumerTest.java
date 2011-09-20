package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
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
import com.google.common.collect.ImmutableSet;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.Stem.Scope;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.misc.SaveMode;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.provider.SubjectTypeEnum;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { GrouperUtil.class, GroupFinder.class, GrouperSession.class, SubjectFinder.class, Group.class })
public class CourseGroupEsbConsumerTest extends TestCase {

	private CourseGroupEsbConsumer consumer;
	private HttpCourseGroupNakamuraManagerImpl nakamuraManager;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	private static final long SEQUENCE_NUMBER = 25;

	private static final String PARENT_DESCRIPTION = "parent description";
	private static final String INST_COURSE_STEM = "inst:sis:courses";
	private static final String APP_COURSE_STEM = "app:sakaioae:courses";

	private static final String course1LecturersApplicationGroupName  = APP_COURSE_STEM  + ":course1:lecturers";
	private static final String course1LecturersInstitutionalGroupName = INST_COURSE_STEM + ":course1:lecturers";
	private static final String course1StudentsApplicationGroupName   = APP_COURSE_STEM  + ":course1:students";
	private static final String course1StudentsInstitutionalGroupName = INST_COURSE_STEM + ":course1:students";
	private static final String course1AllApplicationGroupName        = APP_COURSE_STEM  + ":course1:all";
	private static final String course1AllInstitutionalGroupName      = INST_COURSE_STEM + ":course1:all";

	private String courseGroupId = "course1";
	private String courseStudentGroupId = "course1-student";
	private String courseLecturerGroupId = "course1-lecturer";

	private String invalidCourseGroupName = "edu:apps:sakaioae:simplegroups:x:managers";

	private GrouperSession session;
	private Stem stem;
	private Group group;
	private Group appAllGroup;
	private Subject instGroupSubject;
	private Subject appGroupSubject;
	private Group instLecturerGroup;

	private Member member1;
	private Member member2;

	public void setUp(){

		suppress(method(GrouperUtil.class, "getLog"));
		// Static classes
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		mockStatic(SubjectFinder.class);
		mockStatic(Group.class);

		// Shared Grouper objects
		member1 = mock(Member.class);
		member2 = mock(Member.class);

		when(member1.getSubjectId()).thenReturn("user1");
		when(member1.getSubjectType()).thenReturn(SubjectTypeEnum.PERSON);
		when(member1.isImmediateMember(any(Group.class))).thenReturn(true);
		when(member2.getSubjectId()).thenReturn("user2");
		when(member2.getSubjectType()).thenReturn(SubjectTypeEnum.PERSON);
		when(member2.isImmediateMember(any(Group.class))).thenReturn(true);

		stem = mock(Stem.class);
		when(stem.getDescription()).thenReturn(PARENT_DESCRIPTION);

		group = mock(Group.class);
		when(group.getParentStem()).thenReturn(stem);
		when(group.getExtension()).thenReturn("students");
		when(group.getName()).thenReturn(course1StudentsInstitutionalGroupName);

		session = mock(GrouperSession.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		entry = mock(ChangeLogEntry.class);
		when(entry.getSequenceNumber()).thenReturn(SEQUENCE_NUMBER);

		appAllGroup = mock(Group.class);
		when(Group.saveGroup(session, null, null, course1AllApplicationGroupName,
				BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true)).thenReturn(appAllGroup);

		instGroupSubject = mock(Subject.class);
		when(SubjectFinder.findByIdOrIdentifier(course1StudentsInstitutionalGroupName, false)).thenReturn(instGroupSubject);


		instLecturerGroup = mock(Group.class);
		when(instLecturerGroup.getExtension()).thenReturn("lecturers");
		when(instLecturerGroup.getName()).thenReturn(course1LecturersInstitutionalGroupName);

		appGroupSubject = mock(Subject.class);
		when(SubjectFinder.findByIdOrIdentifier(course1StudentsApplicationGroupName, false)).thenReturn(appGroupSubject);

		nakamuraManager = mock(HttpCourseGroupNakamuraManagerImpl.class);

		groupIdAdapter = mock(GroupIdAdapterImpl.class);
		when(groupIdAdapter.isCourseGroup(course1LecturersApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1StudentsApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1StudentsInstitutionalGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1AllApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(course1AllInstitutionalGroupName)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(invalidCourseGroupName)).thenReturn(false);

		when(groupIdAdapter.isInstitutional(course1StudentsApplicationGroupName)).thenReturn(false);
		when(groupIdAdapter.isInstitutional(course1StudentsInstitutionalGroupName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(course1AllApplicationGroupName)).thenReturn(false);
		when(groupIdAdapter.isInstitutional(course1AllInstitutionalGroupName)).thenReturn(true);

		when(groupIdAdapter.getAllGroup(course1LecturersApplicationGroupName)).thenReturn(course1AllApplicationGroupName);
		when(groupIdAdapter.getAllGroup(course1StudentsApplicationGroupName)).thenReturn(course1AllApplicationGroupName);
		when(groupIdAdapter.getAllGroup(course1StudentsInstitutionalGroupName)).thenReturn(course1AllInstitutionalGroupName);

		when(groupIdAdapter.toProvisioned(course1AllInstitutionalGroupName)).thenReturn(course1AllApplicationGroupName);
		when(groupIdAdapter.toProvisioned(course1StudentsInstitutionalGroupName)).thenReturn(course1StudentsApplicationGroupName);

		when(groupIdAdapter.getPseudoGroupParent(anyString())).thenReturn(courseGroupId);

		when(groupIdAdapter.getGroupId(course1StudentsApplicationGroupName)).thenReturn(courseStudentGroupId);
		when(groupIdAdapter.getGroupId(course1StudentsInstitutionalGroupName)).thenReturn(courseStudentGroupId);
		when(groupIdAdapter.getGroupId(course1LecturersInstitutionalGroupName)).thenReturn(courseLecturerGroupId);

		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		consumer = new CourseGroupEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdAdapter = groupIdAdapter;
		consumer.configurationLoaded = true;
		consumer.pseudoGroupSuffixes = ImmutableSet.of("student", "manager", "member", "ta", "lecturer");
		consumer.triggerRole = "students";
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
		when(entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName))
			.thenReturn(course1AllApplicationGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));

		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName))
			.thenReturn(course1AllInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotACourseGroup(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(invalidCourseGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsApplicationGroupName);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontAllowProvisionedByDefault(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testAllowProvisioned(){
		consumer.allowInstitutional = true;
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testAddGroup() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsApplicationGroupName);
		when(groupIdAdapter.isCourseGroup(course1StudentsApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(course1StudentsApplicationGroupName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(entry));

		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);

		when(groupIdAdapter.getGroupId(course1StudentsApplicationGroupName)).thenReturn(courseStudentGroupId);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(false);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).createGroup(course1StudentsApplicationGroupName, PARENT_DESCRIPTION);
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
		consumer.allowInstitutional = true;
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		when(GroupFinder.findByName(session, course1StudentsInstitutionalGroupName, false)).thenReturn(group);

		when(groupIdAdapter.toProvisioned(course1StudentsInstitutionalGroupName))
			.thenReturn(course1StudentsApplicationGroupName);
		when(groupIdAdapter.getAllGroup(course1StudentsApplicationGroupName))
			.thenReturn(course1AllApplicationGroupName);

		Group appAllGroup = mock(Group.class);
		when(GroupFinder.findByName(session, course1AllApplicationGroupName, false)).thenReturn(appAllGroup);

		Subject instGroupSubject = mock(Subject.class);
		when(SubjectFinder.findByIdOrIdentifier(course1StudentsInstitutionalGroupName, false)).thenReturn(instGroupSubject);

		consumer.allowInstitutional = true;
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(appAllGroup).addMember(instGroupSubject, false);
		verify(nakamuraManager).createGroup(course1StudentsApplicationGroupName, PARENT_DESCRIPTION);
	}

	public void testAddInstitutionalAllGroup() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(false);

		when(groupIdAdapter.toProvisioned(course1StudentsInstitutionalGroupName))
			.thenReturn(course1StudentsApplicationGroupName);
		when(groupIdAdapter.getAllGroup(course1StudentsApplicationGroupName))
			.thenReturn(course1AllApplicationGroupName);

		Group instAllGroup = mock(Group.class);
		when(GroupFinder.findByName(session, course1StudentsInstitutionalGroupName, false)).thenReturn(group);
		when(GroupFinder.findByName(session, course1AllApplicationGroupName, false)).thenReturn(null);
		when(GroupFinder.findByName(session, course1AllInstitutionalGroupName, false)).thenReturn(instAllGroup);

		consumer.allowInstitutional = true;
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(appAllGroup).addMember(instGroupSubject, false);
		verifyStatic();
		Group.saveGroup(session, null, null, course1AllApplicationGroupName,
				BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true);
	}

	public void testDeleteGroupIsNotNull() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name))
			.thenReturn(course1StudentsApplicationGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(true);
		when(groupIdAdapter.isIncludeExcludeSubGroup(course1StudentsApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).groupExists(courseGroupId);
		verify(nakamuraManager).deleteGroup(courseGroupId, course1StudentsApplicationGroupName);
	}

	public void testDeleteGroup() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name))
			.thenReturn(course1StudentsApplicationGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(true);
		when(groupIdAdapter.isIncludeExcludeSubGroup(course1StudentsApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(null);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).groupExists(courseGroupId);
		verify(nakamuraManager).deleteGroup(courseGroupId, course1StudentsApplicationGroupName);
	}

	public void testAddAdminAsLecturer() throws GroupModificationException, UserModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsApplicationGroupName);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);
		when(groupIdAdapter.getGroupId(course1LecturersApplicationGroupName)).thenReturn(courseLecturerGroupId);

		consumer.addAdminAs = "lecturer";
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).createGroup(course1StudentsApplicationGroupName, PARENT_DESCRIPTION);
		verify(nakamuraManager).addMembership(courseLecturerGroupId, "admin");
	}

	public void testAddGroupType() throws GroupModificationException, UserModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_TYPE_ASSIGN)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_TYPE_ASSIGN.groupName))
			.thenReturn(course1StudentsInstitutionalGroupName);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_TYPE_ASSIGN.typeName))
			.thenReturn(BaseGroupEsbConsumer.DEFAULT_GROUP_TYPE_NAME_TRIGGER);

		when(GroupFinder.findByName(session, course1StudentsInstitutionalGroupName, false)).thenReturn(group);
		when(GroupFinder.findByName(session, course1LecturersInstitutionalGroupName, false)).thenReturn(instLecturerGroup);
		when(stem.getChildGroups(Scope.ONE)).thenReturn(ImmutableSet.of(group, instLecturerGroup));
		when(group.getMembers()).thenReturn(ImmutableSet.of(member1, member2));
		when(instLecturerGroup.getMembers()).thenReturn(ImmutableSet.of(member1));

		consumer.allowInstitutional = true;
		consumer.groupTypeNameTrigger = BaseGroupEsbConsumer.DEFAULT_GROUP_TYPE_NAME_TRIGGER;
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).createGroup(course1StudentsApplicationGroupName, PARENT_DESCRIPTION);
		verify(nakamuraManager).addMemberships(courseStudentGroupId, ImmutableList.of("user1", "user2"));
		verify(nakamuraManager).addMemberships(courseLecturerGroupId, ImmutableList.of("user1"));
	}
}
