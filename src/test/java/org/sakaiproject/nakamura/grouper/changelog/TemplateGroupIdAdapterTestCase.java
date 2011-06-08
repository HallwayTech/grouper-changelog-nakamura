package org.sakaiproject.nakamura.grouper.changelog;

import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

import junit.framework.TestCase;

public class TemplateGroupIdAdapterTestCase extends TestCase {
	
	private GroupIdAdapter groupIdAdapter;
	
	@Override
	public void setUp(){
		
	}

	public void testGetNakamuraName(){
		String pattern = "edu:apps:sakaioae:([^:]+):([^:]+):([^:]+)$";
		String template = "'newgroup_' + g[0] + '_' + g[1] + '_' + g[2]";
		groupIdAdapter = new TemplateGroupIdAdapter(pattern, template);
		assertEquals("newgroup_some_thing_else", 
				groupIdAdapter.getNakamuraGroupId("edu:apps:sakaioae:some:thing:else"));
		
	}
	
	public void testGetNakamuraNameCourseStructure(){
		String pattern = "edu:apps:sakaioae:([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)$";
		String template = "g[0] + '_' + g[1] + '_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5]";
		groupIdAdapter = new TemplateGroupIdAdapter(pattern, template); 
													
		assertEquals("acad_term_subject_catalog_session_section", 
				groupIdAdapter.getNakamuraGroupId("edu:apps:sakaioae:acad:term:subject:catalog:session:section"));
	}
}
