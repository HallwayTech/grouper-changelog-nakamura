package org.sakaiproject.nakamura.grouper.changelog.esb;

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
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;

import com.google.common.collect.ImmutableList;

import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
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

	private CourseGroupEsbConsumer consumer;
	private HttpCourseAdapter groupAdapter;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry addEntry;
	private ChangeLogEntry deleteEntry;

	private String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
	private String groupId = "some_course-student";
	private String subjectId = "unittest123";

	public void setUp(){
		// Static stuff
		suppress(method(GrouperUtil.class, "getLog"));
		mockStatic(SubjectFinder.class);

		groupAdapter = mock(HttpCourseAdapter.class);
		groupIdAdapter = mock(GroupIdAdapterImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");
		
		when(groupIdAdapter.isCourseGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(false);
		when(groupIdAdapter.getGroupId(grouperName)).thenReturn("some_course-student");

		consumer = new CourseGroupEsbConsumer();
		consumer.setGroupAdapter(groupAdapter);
		consumer.setGroupIdAdapter(groupIdAdapter);
		consumer.setConfigurationLoaded(true);
		consumer.setPseudoGroupSuffixes("student, manager, member, ta, lecturer");

		addEntry = mock(ChangeLogEntry.class);
		when(addEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)).thenReturn(true);
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName)).thenReturn(grouperName);
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId)).thenReturn(subjectId);
		
		deleteEntry = mock(ChangeLogEntry.class);
		when(deleteEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)).thenReturn(true);
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName)).thenReturn(grouperName);
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId)).thenReturn(subjectId);
	}

	/// ---- Add Membership

	public void testAddMembershipSubjectNull() throws GroupModificationException{
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
		when(groupAdapter.groupExists(groupId)).thenReturn(true);
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(null);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verifyNoMoreInteractions(groupAdapter);
	}

	public void testAddMembershipSubjectIncludeExcludeSubGroup() throws GroupModificationException{
		when(groupIdAdapter.isIncludeExcludeSubGroup(grouperName)).thenReturn(true);
		when(groupAdapter.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));

		Subject subject = mock(Subject.class);
		when(subject.getTypeName()).thenReturn("person");
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(subject);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.setConfigurationLoaded(true);
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);

		verifyNoMoreInteractions(groupAdapter);
	}

	public void testAddMembershipSubjectNotNull() throws GroupModificationException{
		when(groupIdAdapter.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		when(groupAdapter.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));

		Subject subject = mock(Subject.class);
		when(subject.getTypeName()).thenReturn("person");
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);

		verify(groupAdapter).addMembership("some_course-student", subjectId);
	}

	/// ---- Delete Membership

	public void testDeleteMembershipSubjectNull() throws GroupModificationException{
		assertFalse(consumer.ignoreChangelogEntry(deleteEntry));
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(null);
		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);

		verifyNoMoreInteractions(groupAdapter);
	}

	public void testDeleteMembershipSubjectIncludeExcludeSubGroup() throws GroupModificationException{
		when(groupIdAdapter.isIncludeExcludeSubGroup(grouperName)).thenReturn(true);
		when(groupAdapter.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));

		Subject subject = mock(Subject.class);
		when(subject.getTypeName()).thenReturn("person");
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(subject);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);

		verifyNoMoreInteractions(groupAdapter);
	}

	public void testDeleteMembershipSubjectNotNull() throws GroupModificationException{
		when(groupIdAdapter.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		when(groupAdapter.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(deleteEntry));

		Subject subject = mock(Subject.class);
		when(subject.getTypeName()).thenReturn("person");
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);

		verify(groupAdapter).groupExists(groupId);
		verify(groupAdapter).deleteMembership("some_course-student", subjectId);
	}
}