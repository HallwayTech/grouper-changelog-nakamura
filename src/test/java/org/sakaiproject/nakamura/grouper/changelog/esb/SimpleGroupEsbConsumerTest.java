package org.sakaiproject.nakamura.grouper.changelog.esb;

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
import org.sakaiproject.nakamura.grouper.changelog.HttpSimpleGroupNakamuraManagerImpl;

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
@PrepareForTest(value = { GrouperUtil.class, GroupFinder.class, GrouperSession.class, SubjectFinder.class, Group.class })
public class SimpleGroupEsbConsumerTest extends TestCase {

	private SimpleGroupEsbConsumer consumer;
	private HttpSimpleGroupNakamuraManagerImpl nakamuraManager;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	private static final String simplegManagersApplicationGroupName   = "edu:apps:sakaiaoe:provisioned:simplegroups:some:simpleg:managers";
	private static final String simplegManagersInstitutionalGroupName = "inst:sis:simplegroups:some:simpleg:managers";
	private static final String simplegAllApplicationGroupName        = "edu:apps:sakaiaoe:provisioned:simplegroups:some:simpleg:all";
	private static final String simplegAllInstitutionalGroupName      = "inst:sis:simplegroups:some:simpleg:all";
	private static final String invalidGroupName      = "inst:sis:course:some:course:all";

	private static final String groupId = "some_simpleg-manager";
	private static final String parentId = "some_simpleg";

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

		when(groupIdAdapter.getGroupId(simplegManagersApplicationGroupName)).thenReturn(groupId);
		when(groupIdAdapter.getGroupId(simplegManagersInstitutionalGroupName)).thenReturn(groupId);
		when(groupIdAdapter.getPseudoGroupParent(groupId)).thenReturn(parentId);

		when(groupIdAdapter.getAdhocSimpleGroupsStem()).thenReturn("edu:apps:sakaiaoe:adhoc:simplegroups");
		when(groupIdAdapter.getInstitutionalSimpleGroupsStem()).thenReturn("inst:sis:simplegroups");
		when(groupIdAdapter.getProvisionedSimpleGroupsStem()).thenReturn("edu:apps:sakaiaoe:provisioned:simplegroups");

		when(groupIdAdapter.isSimpleGroup(invalidGroupName)).thenReturn(false);
		when(groupIdAdapter.isSimpleGroup(simplegManagersApplicationGroupName)).thenReturn(true);
		when(groupIdAdapter.isSimpleGroup(simplegManagersInstitutionalGroupName)).thenReturn(true);
		when(groupIdAdapter.isSimpleGroup(simplegAllInstitutionalGroupName)).thenReturn(true);

		when(groupIdAdapter.isInstitutional(simplegAllInstitutionalGroupName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(simplegManagersInstitutionalGroupName)).thenReturn(true);

		when(groupIdAdapter.getAllGroup(simplegManagersApplicationGroupName)).thenReturn(simplegAllApplicationGroupName);
		when(groupIdAdapter.toProvisioned(simplegAllInstitutionalGroupName)).thenReturn(simplegManagersApplicationGroupName);

		consumer = new SimpleGroupEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdAdapter = groupIdAdapter;
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
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(simplegAllApplicationGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));

		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(simplegAllInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotASimpleGroup(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(invalidGroupName);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(simplegManagersApplicationGroupName);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontAllowProvisionedByDefault(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(simplegManagersInstitutionalGroupName);
		when(groupIdAdapter.isInstitutional(simplegManagersInstitutionalGroupName)).thenReturn(true);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testAllowProvisioned(){
		consumer.allowInstitutional = true;
		String grouperName = "inst:sis:courses:some:course:students";
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdAdapter.isSimpleGroup(grouperName)).thenReturn(true);
		when(groupIdAdapter.isInstitutional(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}
}