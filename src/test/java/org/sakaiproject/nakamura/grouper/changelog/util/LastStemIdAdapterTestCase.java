package org.sakaiproject.nakamura.grouper.changelog.util;

import junit.framework.TestCase;

public class LastStemIdAdapterTestCase extends TestCase {
	
	LastStemGroupIdAdapter adapter;
	
	@Override
	public void setUp(){
		adapter = new LastStemGroupIdAdapter("edu:apps:stem1");
	}
	
	public void testGetNakamuraGroupId(){
		assertEquals("name1-member", adapter.getNakamuraGroupId("edu:apps:stem1:name1:member"));
		assertEquals("name1-manager", adapter.getNakamuraGroupId("edu:apps:stem1:name1:manager"));
		
		assertEquals("name1_name2-member", adapter.getNakamuraGroupId("edu:apps:stem1:name1_name2:member"));
		assertEquals("name1_name2-manager", adapter.getNakamuraGroupId("edu:apps:stem1:name1_name2:manager"));
	}
}