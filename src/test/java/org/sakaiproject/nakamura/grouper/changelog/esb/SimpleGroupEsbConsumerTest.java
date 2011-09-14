package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import org.sakaiproject.nakamura.grouper.changelog.HttpSimpleGroupNakamuraManagerImpl;
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
import edu.internet2.middleware.grouper.misc.SaveMode;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { GrouperUtil.class, GroupFinder.class, GrouperSession.class, SubjectFinder.class, Group.class })
public class SimpleGroupEsbConsumerTest extends TestCase {

	private SimpleGroupEsbConsumer consumer;
	private HttpSimpleGroupNakamuraManagerImpl nakamuraManager;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	private String grouperName = "edu:apps:sakaiaoe:provisioned:simplegroups:some:simpleg:managers";
	private String allGrouperName = "edu:apps:sakaiaoe:provisioned:simplegroups:some:simpleg:all";
	private String instAllGrouperName = "inst:sis:simplegroups:some:simpleg:all";
	private String instGrouperName = "inst:sis:simplegroups:some:simpleg:managers";


	private String groupId = "some_simpleg-manager";
	private String parentId = "some_simpleg";

	private GrouperSession session;

	private Group group;
	private Stem stem;

	public void setUp(){

		suppress(method(GrouperUtil.class, "getLog"));

		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		mockStatic(SubjectFinder.class);

		stem = mock(Stem.class);
		when(stem.getDescription()).thenReturn("parent description");
		group = mock(Group.class);
		when(group.getParentStem()).thenReturn(stem);

		session = mock(GrouperSession.class);
		when(GrouperSession.startRootSession()).thenReturn(session);

		nakamuraManager = mock(HttpSimpleGroupNakamuraManagerImpl.class);
		groupIdAdapter = mock(GroupIdAdapterImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		when(groupIdAdapter.getGroupId(grouperName)).thenReturn(groupId);
		when(groupIdAdapter.getPseudoGroupParent(groupId)).thenReturn(parentId);

		when(groupIdAdapter.getAdhocSimpleGroupsStem()).thenReturn("edu:apps:sakaiaoe:adhoc:simplegroups");
		when(groupIdAdapter.getInstitutionalSimpleGroupsStem()).thenReturn("inst:sis:simplegroups");
		when(groupIdAdapter.getProvisionedSimpleGroupsStem()).thenReturn("edu:apps:sakaiaoe:provisioned:simplegroups");

		consumer = new SimpleGroupEsbConsumer();
		consumer.setGroupManager(nakamuraManager);
		consumer.setGroupIdAdapter(groupIdAdapter);
		consumer.setConfigurationLoaded(true);
		consumer.setPseudoGroupSuffixes("manager, member");
		consumer.setDeleteRole("managers");

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
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(allGrouperName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnoreAddAll(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(allGrouperName);
		when(groupIdAdapter.isSimpleGroup(allGrouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotASimpleGroup(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(false);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontAllowProvisionedByDefault(){
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
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(false);
		when(groupIdAdapter.getInstitutionalCourseGroupsStem()).thenReturn("no-match");
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);
		when(nakamuraManager.groupExists(groupId)).thenReturn(false);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).createGroup(grouperName, "parent description");
	}

	public void testAddInstitutionalAllGroup() throws GroupModificationException{
		mockStatic(Group.class);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(instAllGrouperName);
		when(groupIdAdapter.isSimpleGroup(instAllGrouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(instAllGrouperName)).thenReturn(true);
		when(nakamuraManager.groupExists(groupId)).thenReturn(false);

		Group appAllGroup = mock(Group.class);
		Group instAllGroup = mock(Group.class);
		when(GroupFinder.findByName(session, allGrouperName, false)).thenReturn(null);
		when(GroupFinder.findByName(session, instAllGrouperName, false)).thenReturn(instAllGroup);

		when(Group.saveGroup(session, null, null, allGrouperName, BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true)).thenReturn(appAllGroup);
		Subject instAllGroupSubject = mock(Subject.class);
		when(SubjectFinder.findByIdOrIdentifier(instAllGrouperName, false)).thenReturn(instAllGroupSubject);

		consumer.allowInstitutional = true;
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(appAllGroup).addMember(instAllGroupSubject);
		verifyStatic();
		Group.saveGroup(session, null, null, allGrouperName, BaseGroupIdAdapter.ALL_GROUP_EXTENSION, null, SaveMode.INSERT, true);
	}

	public void testAddGroupInstitutional() throws GroupModificationException{
		consumer.setAllowInstitutional(true);
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(instGrouperName);
		when(groupIdAdapter.isSimpleGroup(instGrouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(instGrouperName)).thenReturn(true);
		when(groupIdAdapter.getGroupId(instGrouperName)).thenReturn(groupId);

		when(nakamuraManager.groupExists(parentId)).thenReturn(false);
		when(GroupFinder.findByName(session, instGrouperName, false)).thenReturn(group);

		consumer.allowInstitutional = true;
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).groupExists(parentId);
		verify(nakamuraManager).createGroup(grouperName, "parent description");
	}

	public void testDeleteGroupIsNotNull() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(group);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verifyNoMoreInteractions(nakamuraManager);
	}

	public void testDeleteGroup() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(grouperName);
		when(nakamuraManager.groupExists(parentId)).thenReturn(true);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(GroupFinder.findByName(session, grouperName, false)).thenReturn(null);

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).groupExists(parentId);
		verify(nakamuraManager).deleteGroup(parentId, grouperName);
	}
}