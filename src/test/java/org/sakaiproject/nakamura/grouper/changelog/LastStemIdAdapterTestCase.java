package org.sakaiproject.nakamura.grouper.changelog;

import junit.framework.TestCase;

public class LastStemIdAdapterTestCase extends TestCase {
	
	LastStemGroupIdAdapter adapter;
	
	@Override
	public void setUp(){
		adapter = new LastStemGroupIdAdapter("edu:apps:stem1");
	}
	
	public void testGetNakamuraGroupId(){
		assertEquals("name1", adapter.getNakamuraGroupId("edu:apps:stem1:name1:member"));
		assertEquals("name1-manager", adapter.getNakamuraGroupId("edu:apps:stem1:name1:manager"));
		assertEquals("name1-student", adapter.getNakamuraGroupId("edu:apps:stem1:name1:student"));
		assertEquals("name1-ta", adapter.getNakamuraGroupId("edu:apps:stem1:name1:ta"));
		
		assertEquals("name1_name2", adapter.getNakamuraGroupId("edu:apps:stem1:name1_name2:member"));
		assertEquals("name1_name2-manager", adapter.getNakamuraGroupId("edu:apps:stem1:name1_name2:manager"));
		assertEquals("name1_name2-student", adapter.getNakamuraGroupId("edu:apps:stem1:name1_name2:student"));
		assertEquals("name1_name2-ta", adapter.getNakamuraGroupId("edu:apps:stem1:name1_name2:ta"));
	}
}