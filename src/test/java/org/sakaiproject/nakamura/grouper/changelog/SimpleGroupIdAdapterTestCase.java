package org.sakaiproject.nakamura.grouper.changelog;

import junit.framework.TestCase;

public class SimpleGroupIdAdapterTestCase extends TestCase {

	private String PROVISIONED_STEM = "edu:apps:sakaioae:adhoc:groups";

	public void testGetNakamuraIdMembers(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertEquals("newgroup_some_thing_else-members",
				adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:members"));
	}
	
	public void testGetNakamuraIdManagers(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertEquals("newgroup_some_thing_else-manager",
				adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:manager"));
	}
	
	public void testGetNakamuraIdInvalid(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertNull(adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:sssss"));
	}
}
