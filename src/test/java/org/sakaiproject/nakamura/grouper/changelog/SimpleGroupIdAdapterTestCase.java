package org.sakaiproject.nakamura.grouper.changelog;

import junit.framework.TestCase;

public class SimpleGroupIdAdapterTestCase extends TestCase {

	private String PROVISIONED_STEM = "edu:apps:sakaioae:adhoc:groups";

	public void testGetNakamuraId(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertEquals("newgroup_some_thing_else",
				adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:member"));
	}
	
	public void testGetNakamuraIdManagers(){
		SimpleGroupIdAdapter adaptor = new SimpleGroupIdAdapter();
		adaptor.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		assertEquals("newgroup_some_thing_else-manager",
				adaptor.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:manager"));
	}
}
