package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;

import junit.framework.TestCase;

import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

import com.google.common.collect.ImmutableSet;

public class TemplateGroupIdAdapterTestCase extends TestCase {
	
	private Set<String> pseudoGroupSuffxies = ImmutableSet.of("student", "ta", "lecturer", "manager", "members");
	
	public void testGetNakamuraName(){
		String pattern = "edu:apps:sakaioae:([^:]+):([^:]+):([^:]+)$";
		String template = "'newgroup_' + g[0] + '_' + g[1] + '_' + g[2]";
		GroupIdAdapter adaptor = new TemplateGroupIdAdapter(pattern, template, pseudoGroupSuffxies);
		assertEquals("newgroup_some_thing_else", 
				adaptor.getNakamuraGroupId("edu:apps:sakaioae:some:thing:else"));
		
	}
	
	public void testGetNakamuraNameCourseStructure(){
		String pattern = "edu:apps:sakaioae:([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)$";
		String template = "g[0] + '_' + g[1] + '_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5]";
		GroupIdAdapter adaptor = new TemplateGroupIdAdapter(pattern, template, pseudoGroupSuffxies); 
													
		assertEquals("acad_term_subject_catalog_session_section", 
				adaptor.getNakamuraGroupId("edu:apps:sakaioae:acad:term:subject:catalog:session:section"));
	}
	
	public void testGetNakamuraNameNYU(){
		
		String pattern =  "app:atlas:provisioned:groups:([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)";
		String template = "'course_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5] + '_' + g[1] + '_' + g[6]";
		GroupIdAdapter adaptor = new TemplateGroupIdAdapter(pattern, template, pseudoGroupSuffxies); 
				
		assertEquals(null, adaptor.getNakamuraGroupId(null));
		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:lecturer"));
		
		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:student_include"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:lecturer_exclude"));
	}
}
