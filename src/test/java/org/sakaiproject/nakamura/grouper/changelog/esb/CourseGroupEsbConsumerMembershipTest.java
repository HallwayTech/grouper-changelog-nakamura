package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.support.membermodification.MemberMatcher.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;
import junit.framework.TestCase;

import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sakaiproject.nakamura.grouper.changelog.BaseGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdManagerImpl;
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
import edu.internet2.middleware.subject.Subject;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { GrouperUtil.class, GroupFinder.class, GrouperSession.class, SubjectFinder.class })
public class CourseGroupEsbConsumerMembershipTest extends TestCase {

	private static final String instCourseStem =  "inst:sis:courses";
	private static final String provCourseStem = "edu:apps:sakaiaoe:provisioned:courses";
	private static final String adhocCourseStem = "edu:apps:sakaiaoe:adhoc:courses";

	private static final String instGrouperName = "inst:sis:courses:some:course:students";
	private static final String grouperName = "edu:apps:sakaiaoe:provisioned:courses:some:course:students";
	private static final String groupId = "some_course-student";
	private static final String subjectId = "unittest123";

	private static final long SEQUENCE_NUMBER = 25;

	private CourseGroupEsbConsumer consumer;

	// Mocks
	private Subject subject;
	private GrouperSession session;
	private Stem stem;
	private HttpCourseGroupNakamuraManagerImpl nakamuraManager;
	private GroupIdManagerImpl groupIdManager;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry addEntry;
	private ChangeLogEntry deleteEntry;

	public void setUp(){
		// Static stuff
		suppress(method(GrouperUtil.class, "getLog"));
		mockStatic(SubjectFinder.class);
		mockStatic(GroupFinder.class);
		mockStatic(GrouperSession.class);

		session = mock(GrouperSession.class);
		stem = mock(Stem.class);
		subject = mock(Subject.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		addEntry = mock(ChangeLogEntry.class);
		deleteEntry = mock(ChangeLogEntry.class);
		nakamuraManager = mock(HttpCourseGroupNakamuraManagerImpl.class);
		groupIdManager = mock(GroupIdManagerImpl.class);

		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(subject.getTypeName()).thenReturn("person");
		when(subject.getName()).thenReturn(subjectId);

		when(groupIdManager.getGroupId(grouperName)).thenReturn(groupId);
		when(groupIdManager.getPseudoGroupParent(groupId)).thenReturn("some_course");
		when(groupIdManager.toProvisioned(instGrouperName)).thenReturn(grouperName);

		when(groupIdManager.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdManager.isCourseGroup(instGrouperName)).thenReturn(true);

		when(groupIdManager.isInstitutional(grouperName)).thenReturn(false);
		when(groupIdManager.isInstitutional(instGrouperName)).thenReturn(true);

		when(groupIdManager.getGroupId(grouperName)).thenReturn(groupId);
		when(groupIdManager.getGroupId(instGrouperName)).thenReturn(groupId);

		when(groupIdManager.getInstitutionalCourseGroupsStem()).thenReturn(instCourseStem);
		when(groupIdManager.getProvisionedCourseGroupsStem()).thenReturn(provCourseStem);
		when(groupIdManager.getAdhocCourseGroupsStem()).thenReturn(adhocCourseStem);

		when(addEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)).thenReturn(true);
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName)).thenReturn(grouperName);
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId)).thenReturn(subjectId);
		when(addEntry.getSequenceNumber()).thenReturn(SEQUENCE_NUMBER);

		when(deleteEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)).thenReturn(true);
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName)).thenReturn(grouperName);
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId)).thenReturn(subjectId);
		when(deleteEntry.getSequenceNumber()).thenReturn(SEQUENCE_NUMBER);

		consumer = new CourseGroupEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdManager = groupIdManager;
		consumer.allowInstitutional = true;
		consumer.configurationLoaded = true;
		consumer.setPseudoGroupSuffixes("student, manager, member, ta, lecturer");
	}

	/// ---- Add Membership

	public void testAddMembershipSubjectNull() throws GroupModificationException{
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(null);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testAddMembershipSubjectIncludeExcludeSubGroup() throws GroupModificationException{
		when(groupIdManager.isIncludeExcludeSubGroup(grouperName)).thenReturn(true);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(subject);

		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testAddMembershipSubjectNotNull() throws GroupModificationException, UserModificationException{
		when(groupIdManager.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(nakamuraManager).addMembership("some_course-student", subjectId);
	}

	public void testAddMembershipIncludeRemovesProvisionedExcludes() throws GroupModificationException, UserModificationException{
		String provExcludesName = grouperName + BaseGroupIdAdapter.DEFAULT_EXCLUDES_SUFFIX;
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName)).thenReturn(instGrouperName);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(groupIdManager.isIncludeExcludeSubGroup(provExcludesName)).thenReturn(true);
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		Group provisionedExcludesGroup = mock(Group.class);
		when(GroupFinder.findByName(any(GrouperSession.class), eq(provExcludesName), eq(false))).thenReturn(provisionedExcludesGroup);
		when(provisionedExcludesGroup.hasMember(subject)).thenReturn(true);

		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(provisionedExcludesGroup).deleteMember(subject);
		verify(nakamuraManager).addMembership(groupId, subjectId);
	}

	public void testUserModificationExceptionStopsProcessing() throws GroupModificationException, UserModificationException{
		when(groupIdManager.isInstitutional(grouperName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));

		Group group = mock(Group.class);
		when(group.getParentStem()).thenReturn(stem);
		when(stem.getDescription()).thenReturn("parent description");
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		when(nakamuraManager.groupExists("some_course")).thenReturn(false);
		when(groupIdManager.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		doThrow(new UserModificationException()).when(nakamuraManager).createUser(subjectId);

		consumer.addAdminAs = "lecturer";
		consumer.createUsers = true;
		long last = consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		assertEquals(SEQUENCE_NUMBER - 1, last);

		verify(nakamuraManager).createUser(subjectId);
		verifyNoMoreInteractions(nakamuraManager);
	}

	/// ---- Delete Membership

	public void testDeleteMembershipSubjectNull() throws GroupModificationException{
		assertFalse(consumer.ignoreChangelogEntry(deleteEntry));
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(null);
		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);

		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testDeleteMembershipSubjectIncludeExcludeSubGroup() throws GroupModificationException{
		when(groupIdManager.isIncludeExcludeSubGroup(grouperName)).thenReturn(true);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(subject);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testDeleteMembershipSubjectNotNull() throws GroupModificationException{
		when(groupIdManager.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(deleteEntry));
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verify(nakamuraManager).groupExists(groupId);
		verify(nakamuraManager).deleteMembership("some_course-student", subjectId);
	}

	public void testDeleteMembershipIncludeRemovesProvisionedIncludes() throws GroupModificationException, UserModificationException{
		String provIncludesName = grouperName + BaseGroupIdAdapter.DEFAULT_INCLUDES_SUFFIX;
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName)).thenReturn(instGrouperName);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(groupIdManager.isIncludeExcludeSubGroup(provIncludesName)).thenReturn(true);
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		Group provisionedIncludesGroup = mock(Group.class);
		when(GroupFinder.findByName(any(GrouperSession.class), eq(provIncludesName), eq(false))).thenReturn(provisionedIncludesGroup);
		when(provisionedIncludesGroup.hasMember(subject)).thenReturn(true);

		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verify(provisionedIncludesGroup).deleteMember(subject);
		verify(nakamuraManager).deleteMembership(groupId, subjectId);
	}
}