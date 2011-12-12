package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.support.membermodification.MemberMatcher.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;

import java.util.Map;

import junit.framework.TestCase;

import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sakaiproject.nakamura.grouper.changelog.BaseGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdManager;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;
import org.sakaiproject.nakamura.grouper.changelog.api.WorldConstants;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry addEntry;
	private ChangeLogEntry deleteEntry;
	private GrouperSession session;
	private Stem stem;
	private Group group;
	private Group appAllGroup;
	private Subject instGroupSubject;
	private Subject appGroupSubject;
	private Group instLecturerGroup;

	private Member member1;
	private Member member2;

	private NakamuraManager nakamuraManager;
	private GroupIdManagerImpl groupIdManager;

	private CourseGroupEsbConsumer consumer;

	public void setUp(){
		suppress(method(GrouperUtil.class, "getLog"));
		// Static classes
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		mockStatic(SubjectFinder.class);
		mockStatic(Group.class);

		metadata = mock(ChangeLogProcessorMetadata.class);
		stem = mock(Stem.class);
		group = mock(Group.class);
		addEntry = mock(ChangeLogEntry.class);
		deleteEntry = mock(ChangeLogEntry.class);
		member1 = mock(Member.class);
		member2 = mock(Member.class);

		instLecturerGroup = mock(Group.class);
		appAllGroup = mock(Group.class);
		instGroupSubject = mock(Subject.class);
		appGroupSubject = mock(Subject.class);

		groupIdManager = mock(GroupIdManagerImpl.class);
		nakamuraManager = mock(NakamuraManager.class);

		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");
		when(addEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(deleteEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(addEntry.getSequenceNumber()).thenReturn(SEQUENCE_NUMBER);

		when(member1.getSubjectId()).thenReturn("user1");
		when(member1.getSubjectType()).thenReturn(SubjectTypeEnum.PERSON);
		when(member1.isImmediateMember(any(Group.class))).thenReturn(true);
		when(member2.getSubjectId()).thenReturn("user2");
		when(member2.getSubjectType()).thenReturn(SubjectTypeEnum.PERSON);
		when(member2.isImmediateMember(any(Group.class))).thenReturn(true);

		when(stem.getDescription()).thenReturn(PARENT_DESCRIPTION);

		when(group.getParentStem()).thenReturn(stem);
		when(group.getExtension()).thenReturn("students");
		when(group.getName()).thenReturn(course1StudentsInstitutionalGroupName);
		when(group.getAttributeOrFieldValue(BaseGroupEsbConsumer.PROP_WORLD_TEMPLATE_PATH, false, false)).thenReturn("worldTemplate");

		when(GrouperSession.startRootSession()).thenReturn(session);

		when(Group.saveGroup(session, null, null, course1AllApplicationGroupName,
				BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true)).thenReturn(appAllGroup);

		when(SubjectFinder.findByIdOrIdentifier(course1StudentsInstitutionalGroupName, false)).thenReturn(instGroupSubject);
		when(SubjectFinder.findByIdOrIdentifier(course1StudentsApplicationGroupName, false)).thenReturn(appGroupSubject);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);
		when(GroupFinder.findByName(session, course1StudentsInstitutionalGroupName, false)).thenReturn(group);

		when(instLecturerGroup.getExtension()).thenReturn("lecturers");
		when(instLecturerGroup.getName()).thenReturn(course1LecturersInstitutionalGroupName);

		when(groupIdManager.getWorldType(course1LecturersApplicationGroupName)).thenReturn(GroupIdManager.COURSE);
		when(groupIdManager.getWorldType(course1StudentsApplicationGroupName)).thenReturn(GroupIdManager.COURSE);
		when(groupIdManager.getWorldType(course1StudentsInstitutionalGroupName)).thenReturn(GroupIdManager.COURSE);
		when(groupIdManager.getWorldType(course1AllApplicationGroupName)).thenReturn(GroupIdManager.COURSE);
		when(groupIdManager.getWorldType(course1AllInstitutionalGroupName)).thenReturn(GroupIdManager.COURSE);
		when(groupIdManager.getWorldType(invalidCourseGroupName)).thenReturn(null);

		when(groupIdManager.isInstitutional(course1StudentsApplicationGroupName)).thenReturn(false);
		when(groupIdManager.isInstitutional(course1StudentsInstitutionalGroupName)).thenReturn(true);
		when(groupIdManager.isInstitutional(course1AllApplicationGroupName)).thenReturn(false);
		when(groupIdManager.isInstitutional(course1AllInstitutionalGroupName)).thenReturn(true);

		when(groupIdManager.getAllGroupName(course1LecturersApplicationGroupName)).thenReturn(course1AllApplicationGroupName);
		when(groupIdManager.getAllGroupName(course1StudentsApplicationGroupName)).thenReturn(course1AllApplicationGroupName);
		when(groupIdManager.getAllGroupName(course1StudentsInstitutionalGroupName)).thenReturn(course1AllInstitutionalGroupName);

		when(groupIdManager.getApplicationGroupName(course1AllInstitutionalGroupName)).thenReturn(course1AllApplicationGroupName);
		when(groupIdManager.getApplicationGroupName(course1StudentsInstitutionalGroupName)).thenReturn(course1StudentsApplicationGroupName);

		when(groupIdManager.getWorldId(anyString())).thenReturn(courseGroupId);

		when(groupIdManager.getGroupId(course1StudentsApplicationGroupName)).thenReturn(courseStudentGroupId);
		when(groupIdManager.getGroupId(course1StudentsInstitutionalGroupName)).thenReturn(courseStudentGroupId);
		when(groupIdManager.getGroupId(course1LecturersInstitutionalGroupName)).thenReturn(courseLecturerGroupId);

		consumer = new CourseGroupEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdManager = groupIdManager;
		consumer.configurationLoaded = true;
		consumer.pseudoGroupSuffixes = ImmutableSet.of("student", "manager", "member", "ta", "lecturer");
		consumer.triggerRole = "students";
		consumer.addAdminAs = "ta";
	}

	public void testIgnoreInvalidEntryType() throws Exception{
		ChangeLogEntry bad = mock(ChangeLogEntry.class);
		when(bad.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(false);
		when(bad.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(false);
		when(bad.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_UPDATE)).thenReturn(false);
		when(bad.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)).thenReturn(false);
		when(bad.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)).thenReturn(false);
		assertTrue(consumer.ignoreChangelogEntry(bad));
	}

	public void testIgnoreAll(){
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1AllApplicationGroupName);
		assertTrue(consumer.ignoreChangelogEntry(addEntry));

		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1AllInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testIgnoreNotACourseGroup(){
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(invalidCourseGroupName);
		assertTrue(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testDontIgnore(){
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsApplicationGroupName);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testDontAllowProvisionedByDefault(){
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testAllowProvisioned(){
		consumer.allowInstitutional = true;
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testAddGroup() throws GroupModificationException{
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsApplicationGroupName);
		when(groupIdManager.isInstitutional(course1StudentsApplicationGroupName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));

		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);
		when(groupIdManager.getGroupId(course1StudentsApplicationGroupName)).thenReturn(courseStudentGroupId);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(false);
		when(groupIdManager.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(nakamuraManager).createWorld(course1StudentsApplicationGroupName, courseGroupId, courseGroupId, "parent description",
				new String[0], WorldConstants.MEMBERS_ONLY, WorldConstants.NO, "worldTemplate", "",
				ImmutableMap.of("admin", "ta"));
	}

	public void testAddGroupDefaultTitle() throws GroupModificationException{
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsApplicationGroupName);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);
		when(groupIdManager.getInstitutionalCourseGroupsStem()).thenReturn("no-match");
		when(stem.getDescription()).thenReturn(null);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(false);

		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(nakamuraManager).createWorld(course1StudentsApplicationGroupName, courseGroupId, courseGroupId, courseGroupId,
				new String[0], WorldConstants.MEMBERS_ONLY, WorldConstants.NO, "worldTemplate", "",
				ImmutableMap.of("admin", "ta"));
	}

	public void testAddGroupInstitutional() throws GroupModificationException{
		consumer.allowInstitutional = true;
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		when(GroupFinder.findByName(session, course1StudentsInstitutionalGroupName, false)).thenReturn(group);
		when(groupIdManager.getAllGroupName(course1StudentsApplicationGroupName))
			.thenReturn(course1AllApplicationGroupName);

		Group appAllGroup = mock(Group.class);
		when(GroupFinder.findByName(session, course1AllApplicationGroupName, false)).thenReturn(appAllGroup);

		Subject instGroupSubject = mock(Subject.class);
		when(SubjectFinder.findByIdOrIdentifier(course1StudentsInstitutionalGroupName, false)).thenReturn(instGroupSubject);

		consumer.allowInstitutional = true;
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(appAllGroup).addMember(instGroupSubject, false);
		verify(nakamuraManager).createWorld(course1StudentsApplicationGroupName, courseGroupId, courseGroupId, "parent description", 
				new String[0], WorldConstants.MEMBERS_ONLY, WorldConstants.NO, "worldTemplate", "", 
				ImmutableMap.of("admin", "ta"));
	}

	public void testAddInstitutionalAllGroup() throws GroupModificationException{
		when(addEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name))
			.thenReturn(course1StudentsInstitutionalGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(false);
		when(groupIdManager.getAllGroupName(course1StudentsApplicationGroupName))
			.thenReturn(course1AllApplicationGroupName);

		Group instAllGroup = mock(Group.class);
		when(GroupFinder.findByName(session, course1StudentsInstitutionalGroupName, false)).thenReturn(group);
		when(GroupFinder.findByName(session, course1AllApplicationGroupName, false)).thenReturn(null);
		when(GroupFinder.findByName(session, course1AllInstitutionalGroupName, false)).thenReturn(instAllGroup);

		consumer.allowInstitutional = true;
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(appAllGroup).addMember(instGroupSubject, false);
		verifyStatic();
		Group.saveGroup(session, null, null, course1AllApplicationGroupName,
				BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true);
	}

	public void testDeleteGroupIsNotNull() throws GroupModificationException{
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name))
			.thenReturn(course1StudentsApplicationGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(true);
		when(groupIdManager.isIncludeExcludeSubGroup(course1StudentsApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);

		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verify(nakamuraManager).groupExists(courseGroupId);
		verify(nakamuraManager).deleteGroup(courseGroupId, course1StudentsApplicationGroupName);
	}

	public void testDeleteGroup() throws GroupModificationException{
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name))
			.thenReturn(course1StudentsApplicationGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(true);
		when(groupIdManager.isIncludeExcludeSubGroup(course1StudentsApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(null);

		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verify(nakamuraManager).groupExists(courseGroupId);
		verify(nakamuraManager).deleteGroup(courseGroupId, course1StudentsApplicationGroupName);
	}

	public void testDeleteGroupDisabled() throws GroupModificationException{
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name))
			.thenReturn(course1StudentsApplicationGroupName);
		when(nakamuraManager.groupExists(courseGroupId)).thenReturn(true);
		when(groupIdManager.isIncludeExcludeSubGroup(course1StudentsApplicationGroupName)).thenReturn(false);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(null);

		consumer.deleteGroups = false;
		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testAddAdminAsLecturer() throws GroupModificationException, UserModificationException{
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(course1StudentsApplicationGroupName);
		when(GroupFinder.findByName(session, course1StudentsApplicationGroupName, false)).thenReturn(group);
		when(groupIdManager.getGroupId(course1LecturersApplicationGroupName)).thenReturn(courseLecturerGroupId);

		consumer.addAdminAs = "lecturer";
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(nakamuraManager).createWorld(course1StudentsApplicationGroupName, courseGroupId, courseGroupId, "parent description",
				new String[0], WorldConstants.MEMBERS_ONLY, WorldConstants.NO, "worldTemplate", "",
				ImmutableMap.of("admin", "lecturer"));
		verify(nakamuraManager).addMembership(courseLecturerGroupId, "admin");
	}

	public void testAddGroupType() throws GroupModificationException, UserModificationException{
		ChangeLogEntry entry = mock(ChangeLogEntry.class);
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
		verify(nakamuraManager, times(2)).createUser("user1");
		verify(nakamuraManager).createUser("user2");
		verify(nakamuraManager).createWorld(course1StudentsApplicationGroupName, courseGroupId, courseGroupId, "parent description",
				new String[0], WorldConstants.MEMBERS_ONLY, WorldConstants.NO, "worldTemplate", "",
				ImmutableMap.of("admin", "ta"));
		verify(nakamuraManager).addMemberships(courseStudentGroupId, ImmutableList.of("user1", "user2"));
		verify(nakamuraManager).addMemberships(courseLecturerGroupId, ImmutableList.of("user1"));
	}
}
