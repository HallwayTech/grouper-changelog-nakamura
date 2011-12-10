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
import org.sakaiproject.nakamura.grouper.changelog.GroupIdManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdManager;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;

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

	private static final String simplegManagersApplicationGroupName   = "edu:apps:sakaiaoe:provisioned:simplegroups:some:simpleg:managers";
	private static final String simplegManagersInstitutionalGroupName = "inst:sis:simplegroups:some:simpleg:managers";
	private static final String simplegAllApplicationGroupName        = "edu:apps:sakaiaoe:provisioned:simplegroups:some:simpleg:all";
	private static final String simplegAllInstitutionalGroupName      = "inst:sis:simplegroups:some:simpleg:all";
	private static final String invalidGroupName      = "inst:sis:course:some:course:all";

	private static final String groupId = "some_simpleg-manager";
	private static final String parentId = "some_simpleg";

	private SimpleGroupEsbConsumer consumer;
	private NakamuraManager nakamuraManager;
	private GroupIdManagerImpl groupIdManager;

	private GrouperSession session;
	private Group group;
	private Stem stem;

	private ChangeLogEntry addEntry;
	private ChangeLogEntry deleteEntry;
	private ChangeLogProcessorMetadata metadata;

	public void setUp(){

		suppress(method(GrouperUtil.class, "getLog"));

		mockStatic(GrouperSession.class);
		mockStatic(GroupFinder.class);
		mockStatic(SubjectFinder.class);

		addEntry = mock(ChangeLogEntry.class);
		deleteEntry = mock(ChangeLogEntry.class);

		when(addEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(deleteEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);

		stem = mock(Stem.class);
		when(stem.getDescription()).thenReturn("parent description");
		group = mock(Group.class);
		when(group.getParentStem()).thenReturn(stem);

		session = mock(GrouperSession.class);
		when(GrouperSession.startRootSession()).thenReturn(session);

		nakamuraManager = mock(NakamuraManager.class);
		groupIdManager = mock(GroupIdManagerImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		when(groupIdManager.getGroupId(simplegManagersApplicationGroupName)).thenReturn(groupId);
		when(groupIdManager.getGroupId(simplegManagersInstitutionalGroupName)).thenReturn(groupId);
		when(groupIdManager.getPseudoGroupParent(groupId)).thenReturn(parentId);

		when(groupIdManager.getAdhocSimpleGroupsStem()).thenReturn("edu:apps:sakaiaoe:adhoc:simplegroups");
		when(groupIdManager.getInstitutionalSimpleGroupsStem()).thenReturn("inst:sis:simplegroups");
		when(groupIdManager.getProvisionedSimpleGroupsStem()).thenReturn("edu:apps:sakaiaoe:provisioned:simplegroups");

		when(groupIdManager.getWorldType(invalidGroupName)).thenReturn(null);
		when(groupIdManager.getWorldType(simplegManagersApplicationGroupName)).thenReturn(GroupIdManager.SIMPLE);
		when(groupIdManager.getWorldType(simplegManagersInstitutionalGroupName)).thenReturn(GroupIdManager.SIMPLE);
		when(groupIdManager.getWorldType(simplegAllInstitutionalGroupName)).thenReturn(GroupIdManager.SIMPLE);

		when(groupIdManager.isInstitutional(simplegAllInstitutionalGroupName)).thenReturn(true);
		when(groupIdManager.isInstitutional(simplegManagersInstitutionalGroupName)).thenReturn(true);

		when(groupIdManager.getAllGroup(simplegManagersApplicationGroupName)).thenReturn(simplegAllApplicationGroupName);
		when(groupIdManager.toProvisioned(simplegAllInstitutionalGroupName)).thenReturn(simplegManagersApplicationGroupName);

		consumer = new SimpleGroupEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdManager = groupIdManager;
		consumer.configurationLoaded = true;
		consumer.triggerRole = "managers";
		consumer.setPseudoGroupSuffixes("manager, member");

		addEntry = mock(ChangeLogEntry.class);
		when(addEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		deleteEntry = mock(ChangeLogEntry.class);
		when(deleteEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)).thenReturn(true);
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
		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(simplegAllApplicationGroupName);
		assertTrue(consumer.ignoreChangelogEntry(deleteEntry));

		when(deleteEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name)).thenReturn(simplegAllInstitutionalGroupName);
		assertTrue(consumer.ignoreChangelogEntry(deleteEntry));
	}

	public void testIgnoreNotASimpleGroup(){
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(invalidGroupName);
		assertTrue(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testDontIgnore(){
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(simplegManagersApplicationGroupName);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testDontAllowProvisionedByDefault(){
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(simplegManagersInstitutionalGroupName);
		when(groupIdManager.isInstitutional(simplegManagersInstitutionalGroupName)).thenReturn(true);
		assertTrue(consumer.ignoreChangelogEntry(addEntry));
	}

	public void testAllowProvisioned(){
		consumer.allowInstitutional = true;
		String grouperName = "inst:sis:courses:some:course:students";
		when(addEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName);
		when(groupIdManager.getWorldType(grouperName)).thenReturn(GroupIdManager.SIMPLE);
		when(groupIdManager.isInstitutional(grouperName)).thenReturn(true);
		assertFalse(consumer.ignoreChangelogEntry(addEntry));
	}
}