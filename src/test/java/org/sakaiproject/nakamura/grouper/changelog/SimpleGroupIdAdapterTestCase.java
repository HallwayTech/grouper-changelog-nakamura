package org.sakaiproject.nakamura.grouper.changelog;

import org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer;

import junit.framework.TestCase;

public class SimpleGroupIdAdapterTestCase extends TestCase {

	private String PROVISIONED_STEM = "edu:apps:sakaioae:adhoc:groups";

	public void testGetNakamuraIdMembers(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertEquals("newgroup_some_thing_else-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX,
				adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:" + SimpleGroupEsbConsumer.MEMBER_SUFFIX));
	}
	
	public void testGetNakamuraIdManagers(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertEquals("newgroup_some_thing_else-" + SimpleGroupEsbConsumer.MANAGER_SUFFIX,
				adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:" + SimpleGroupEsbConsumer.MANAGER_SUFFIX));
	}
	
	public void testGetNakamuraIdInvalid(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertNull(adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:sssss"));
	}
}
