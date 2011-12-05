package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
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
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;

import com.google.common.collect.ImmutableList;

import edu.internet2.middleware.grouper.Group;
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
public class SimpleGroupEsbConsumerMembershipTest extends TestCase {

	private SimpleGroupEsbConsumer consumer;
	private NakamuraManager nakamuraManager;
	private GroupIdManagerImpl groupIdManager;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry addEntry;
	private ChangeLogEntry deleteEntry;

	private static final String instSimpleStem =  "inst:sis:simplegroups";
	private static final String provSimpleStem = "edu:apps:sakaiaoe:provisioned:simplegroups";
	private static final String adhocSimpleGroupsStem = "edu:apps:sakaiaoe:adhoc:simplegroups";

	private static final String instGrouperName =  "inst:sis:simplegroups:some:course:students";
	private static final String grouperName = "edu:apps:sakaiaoe:provisioned:simplegroups:some:course:students";
	private static final String groupId = "some_course-student";
	private static final String subjectId = "unittest123";

	private Subject subject;

	public void setUp(){
		// Static stuff
		suppress(method(GrouperUtil.class, "getLog"));
		mockStatic(SubjectFinder.class);
		mockStatic(GroupFinder.class);
		mockStatic(GrouperSession.class);

		when(GrouperSession.startRootSession()).thenReturn(mock(GrouperSession.class));

		subject = mock(Subject.class);
		when(subject.getTypeName()).thenReturn("person");
		when(subject.getName()).thenReturn(subjectId);

		nakamuraManager = mock(NakamuraManager.class);
		groupIdManager = mock(GroupIdManagerImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		when(groupIdManager.isSimpleGroup(grouperName)).thenReturn(true);
		when(groupIdManager.isSimpleGroup(instGrouperName)).thenReturn(true);

		when(groupIdManager.isInstitutional(grouperName)).thenReturn(false);
		when(groupIdManager.isInstitutional(instGrouperName)).thenReturn(true);

		when(groupIdManager.getGroupId(grouperName)).thenReturn(groupId);
		when(groupIdManager.getGroupId(instGrouperName)).thenReturn(groupId);
		when(groupIdManager.toProvisioned(instGrouperName)).thenReturn(grouperName);

		when(groupIdManager.getInstitutionalSimpleGroupsStem()).thenReturn(instSimpleStem);
		when(groupIdManager.getProvisionedSimpleGroupsStem()).thenReturn(provSimpleStem);
		when(groupIdManager.getAdhocSimpleGroupsStem()).thenReturn(adhocSimpleGroupsStem);

		consumer = new SimpleGroupEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdManager = groupIdManager;
		consumer.configurationLoaded = true;
		consumer.allowInstitutional = true;
		consumer.setPseudoGroupSuffixes("manager, member");

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
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(null);
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testAddMembershipSubjectIncludeExcludeSubGroup() throws GroupModificationException{
		when(groupIdManager.isIncludeExcludeSubGroup(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
		when(SubjectFinder.findByIdentifier(subjectId, false)).thenReturn(subject);

		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testAddMembershipSubjectNotNull() throws GroupModificationException, UserModificationException{
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(groupIdManager.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));

		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);
		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);

		verify(nakamuraManager).addMembership(groupId, subjectId);
	}

	public void testAddMembershipIncludeRemovesProvisionedExcludes() throws GroupModificationException, UserModificationException{
		String excludesName = grouperName + BaseGroupIdAdapter.DEFAULT_EXCLUDES_SUFFIX;
		String includesName = grouperName + BaseGroupIdAdapter.DEFAULT_INCLUDES_SUFFIX;
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName)).thenReturn(instGrouperName);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(groupIdManager.isIncludeExcludeSubGroup(excludesName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));

		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		Group includesGroup = mock(Group.class);
		when(GroupFinder.findByName(any(GrouperSession.class), eq(includesName), eq(false))).thenReturn(includesGroup);
		when(includesGroup.hasMember(subject)).thenReturn(true);
		Group excludesGroup = mock(Group.class);
		when(GroupFinder.findByName(any(GrouperSession.class), eq(excludesName), eq(false))).thenReturn(excludesGroup);
		when(excludesGroup.hasMember(subject)).thenReturn(true);

		consumer.processChangeLogEntries(ImmutableList.of(addEntry), metadata);
		verify(includesGroup).deleteMember(subject);
		verify(excludesGroup).deleteMember(subject);
		verify(nakamuraManager).addMembership(groupId, subjectId);
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
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testDeleteMembershipSubjectNotNull() throws GroupModificationException{
		when(groupIdManager.isIncludeExcludeSubGroup(grouperName)).thenReturn(false);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(deleteEntry));
		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verify(nakamuraManager).deleteMembership(groupId, subjectId);
	}

	public void testDeleteMembershipIncludeRemovesProvisionedIncludesExcludes() throws GroupModificationException, UserModificationException{
		String includesName = grouperName + BaseGroupIdAdapter.DEFAULT_INCLUDES_SUFFIX;
		String excludesName = grouperName + BaseGroupIdAdapter.DEFAULT_EXCLUDES_SUFFIX;
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName)).thenReturn(instGrouperName);
		when(nakamuraManager.groupExists(groupId)).thenReturn(true);
		when(groupIdManager.getGroupId(instGrouperName)).thenReturn(groupId);
		when(groupIdManager.isIncludeExcludeSubGroup(includesName)).thenReturn(true);
		when(groupIdManager.isInstitutional(instGrouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(deleteEntry));

		when(SubjectFinder.findByIdOrIdentifier(subjectId, false)).thenReturn(subject);

		Group includesGroup = mock(Group.class);
		when(GroupFinder.findByName(any(GrouperSession.class), eq(includesName), eq(false))).thenReturn(includesGroup);
		when(includesGroup.hasMember(subject)).thenReturn(true);
		Group excludesGroup = mock(Group.class);
		when(GroupFinder.findByName(any(GrouperSession.class), eq(excludesName), eq(false))).thenReturn(excludesGroup);
		when(excludesGroup.hasMember(subject)).thenReturn(true);

		consumer.processChangeLogEntries(ImmutableList.of(deleteEntry), metadata);
		verify(includesGroup).deleteMember(subject);
		verify(excludesGroup).deleteMember(subject);
		verify(nakamuraManager).deleteMembership(groupId, subjectId);
	}
}