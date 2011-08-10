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
import org.sakaiproject.nakamura.grouper.changelog.HttpSimpleGroupAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;

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
public class SimpleGroupEsbConsumerTest extends TestCase {

	private SimpleGroupEsbConsumer consumer;
	private HttpSimpleGroupAdapter groupAdapter;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	public void setUp(){
		groupAdapter = mock(HttpSimpleGroupAdapter.class);
		groupIdAdapter = mock(GroupIdAdapterImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		consumer = new SimpleGroupEsbConsumer();
		consumer.setGroupAdapter(groupAdapter);
		consumer.setGroupIdAdapter(groupIdAdapter);
		consumer.setConfigurationLoaded(true);
		consumer.setPseudoGroupSuffixes("manager, member");
		suppress(method(GrouperUtil.class, "getLog"));

		entry = mock(ChangeLogEntry.class);
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

	public void testIgnoreNotASimpleGroup(){
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(false);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontAllowProvisionedByDefault(){
		String grouperName = "inst:sis:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testAllowProvisioned(){
		consumer.setAllowInstitutional(true);
		String grouperName = "inst:sis:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testAddGroup() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
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
		when(groupAdapter.groupExists("some_course")).thenReturn(false);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.setConfigurationLoaded(true);
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(groupAdapter).createGroup(grouperName, "parent description");
	}

	public void testAddGroupInstitutional() throws GroupModificationException{
		consumer.setAllowInstitutional(true);
		String grouperName = "inst:sis:simplegroups:some:simpleg:students";
		String rewrittenGrouperName = "edu:apps:sakaiaoe:simplegroups:some:simpleg:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("inst:sis:simplegroups");
		when(groupIdAdapter.getProvisionedCourseGroupsStem()).thenReturn("edu:apps:sakaiaoe:simplegroups");
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

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn("some_simpleg-student");
		when(groupIdAdapter.getPseudoGroupParent("some_simpleg-student")).thenReturn("some_course");
		when(groupAdapter.groupExists("some_simpleg")).thenReturn(false);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.setConfigurationLoaded(true);
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(groupAdapter).createGroup(rewrittenGrouperName, "parent description");
	}

	public void testDeleteGroupIsNotNull() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));

		Group group = mock(Group.class);
		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.setConfigurationLoaded(true);
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verifyNoMoreInteractions(groupAdapter);
	}

	public void testDeleteGroup() throws GroupModificationException{
		String grouperName = "edu:apps:sakaiaoe:simplegroups:some:simpleg:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.getGroupId(grouperName)).thenReturn("some_simpleg-student");

		assertFalse(consumer.ignoreChangelogEntry(entry));

		GrouperSession session = mock(GrouperSession.class);
		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		when(GrouperSession.startRootSession()).thenReturn(session);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(null);

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.setConfigurationLoaded(true);
		consumer.setDeleteRole("students");
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(groupAdapter).deleteGroup("some_simpleg-student", grouperName);
	}
}