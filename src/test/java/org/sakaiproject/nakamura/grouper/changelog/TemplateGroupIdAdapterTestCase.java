package org.sakaiproject.nakamura.grouper.changelog;

import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

import junit.framework.TestCase;

public class TemplateGroupIdAdapterTestCase extends TestCase {
	
	private GroupIdAdapter groupIdAdapter;
	
	@Override
	public void setUp(){
		groupIdAdapter = new TemplateGroupIdAdapter("edu:apps:sakaioae:([^:]+):([^:]+):([^:]+)$", 
													"'newgroup_' + g[0] + '_' + g[1] + '_' + g[2]");
	}

	public void testGetNakamuraName(){
		assertEquals("newgroup_some_thing_else", 
				groupIdAdapter.getNakamuraGroupId("edu:apps:sakaioae:some:thing:else"));
	}
}
