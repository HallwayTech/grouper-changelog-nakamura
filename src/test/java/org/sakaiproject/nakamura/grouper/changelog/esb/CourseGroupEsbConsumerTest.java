package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.support.membermodification.MemberMatcher.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;
import junit.framework.TestCase;

import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
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
import edu.internet2.middleware.grouper.util.GrouperUtil;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { GrouperUtil.class, GroupFinder.class, GrouperSession.class, SubjectFinder.class })
public class CourseGroupEsbConsumerTest extends TestCase {

	private CourseGroupEsbConsumer consumer;
	private HttpCourseGroupNakamuraManagerImpl nakamuraManager;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	private static final long SEQUENCE_NUMBER = 25;

	public void setUp(){
		nakamuraManager = mock(HttpCourseGroupNakamuraManagerImpl.class);
		groupIdAdapter = mock(GroupIdAdapterImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		consumer = new CourseGroupEsbConsumer();
		consumer.setGroupManager(nakamuraManager);
		consumer.setGroupIdAdapter(groupIdAdapter);
		consumer.setConfigurationLoaded(true);
		consumer.setPseudoGroupSuffixes("student, manager, member, ta, lecturer");
		consumer.setDeleteRole("students");
		suppress(method(GrouperUtil.class, "getLog"));

		entry = mock(ChangeLogEntry.class);
		when(entry.getSequenceNumber()).thenReturn(SEQUENCE_NUMBER);
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
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn("edu:apps:sakaiaoe:courses:some:course:all");
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotACourseGroup(){
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(false);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontAllowProvisionedByDefault(){
		String grouperName = "inst:sis:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testAllowProvisioned(){
		consumer.setAllowInstitutional(true);
		String grouperName = "inst:sis:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testAddGroup() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(entry));

		Group group = mock(Group.class);
		Stem stem = mock(Stem.class);
		when(group.getParentStem()).thenReturn(stem);
		when(stem.getDescription()).thenReturn("parent description");
		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn("some_course-student");
		when(groupIdAdapter.getPseudoGroupParent("some_course-student")).thenReturn("some_course");
		when(nakamuraManager.groupExists("some_course")).thenReturn(false);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).createGroup(grouperName, "parent description");
	}

	public void testAddGroupDefaultTitle() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		String groupId = "some_course-student";
		String parentId = "some_course";

		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(entry));

		Group group = mock(Group.class);
		Stem stem = mock(Stem.class);
		when(group.getParentStem()).thenReturn(stem);
		when(stem.getDescription()).thenReturn(null);

		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn(groupId);
		when(groupIdAdapter.getPseudoGroupParent(groupId)).thenReturn(parentId);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		when(nakamuraManager.groupExists(parentId)).thenReturn(false);
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).createGroup(grouperName, parentId);
	}

	public void testAddGroupInstitutional() throws GroupModificationException{
		consumer.setAllowInstitutional(true);
		String grouperName = "inst:sis:courses:some:course:students";
		String rewrittenGrouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("inst:sis:courses");
		when(groupIdAdapter.getProvisionedCourseGroupsStem()).thenReturn("edu:apps:sakaiaoe:courses");
		assertFalse(consumer.ignoreChangelogEntry(entry));

		Group group = mock(Group.class);
		Stem stem = mock(Stem.class);
		when(group.getParentStem()).thenReturn(stem);
		when(stem.getDescription()).thenReturn("parent description");
		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn("some_course-student");
		when(groupIdAdapter.getPseudoGroupParent("some_course-student")).thenReturn("some_course");
		when(nakamuraManager.groupExists("some_course")).thenReturn(false);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).createGroup(rewrittenGrouperName, "parent description");
	}

	public void testDeleteGroupIsNotNull() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		String groupId = "some_course-student";

		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(grouperName);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		when(groupIdAdapter.getGroupId(grouperName)).thenReturn(groupId);
		assertFalse(consumer.ignoreChangelogEntry(entry));

		Group group = mock(Group.class);
		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).deleteGroup(groupId, grouperName);
	}

	public void testDeleteGroup() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		String groupId = "some_course-student";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(grouperName);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		when(groupIdAdapter.getGroupId(grouperName)).thenReturn(groupId);

		assertFalse(consumer.ignoreChangelogEntry(entry));

		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(null);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).deleteGroup(groupId, grouperName);
	}

	public void testAddAdminAsLecturer() throws GroupModificationException, UserModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:lecturers";
		String groupId = "some_course-student";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(entry));

		Group group = mock(Group.class);
		Stem stem = mock(Stem.class);
		when(group.getParentStem()).thenReturn(stem);
		when(stem.getDescription()).thenReturn("parent description");
		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn(groupId);
		when(groupIdAdapter.getPseudoGroupParent(groupId)).thenReturn("some_course");
		when(nakamuraManager.groupExists("some_course")).thenReturn(false);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		consumer.addAdminAs = "lecturer";
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).createGroup(grouperName, "parent description");
		verify(nakamuraManager).addMembership("some_course-lecturer", "admin");
	}
}
