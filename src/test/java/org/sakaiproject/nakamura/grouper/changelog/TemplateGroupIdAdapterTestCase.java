package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class TemplateGroupIdAdapterTestCase extends TestCase {

	private Set<String> pseudoGroupSuffixes = ImmutableSet.of("student", "ta", "lecturer", "manager", "members");
	private Set<String> includeExcludeSuffixes = ImmutableSet.of("_includes", "_excludes", "_systemOfRecord", "_systemOfRecordAndIncludes");
	
	TemplateGroupIdAdapter adaptor;
	
	@Override
	public void setUp(){
		adaptor = new TemplateGroupIdAdapter();
		adaptor.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		adaptor.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		adaptor.setProvisionedStem("apps:sakaioae:provisioned:courses");
		adaptor.setAdhocStem("apps:sakaioae:adhoc:courses");
		
	}

	public void testGetNakamuraNameProvisioned(){
		String pattern = "([^:]+):([^:]+):([^:]+)$";
		String template = "'newgroup_' + g[0] + '_' + g[1] + '_' + g[2]";
		adaptor.setPattern(Pattern.compile(pattern));
		adaptor.setNakamuraIdTemplate(template);

		assertEquals("newgroup_some_thing_else",
				adaptor.getNakamuraGroupId("apps:sakaioae:provisioned:courses:some:thing:else"));

	}
	
	public void testGetNakamuraNameAdhoc(){
		assertEquals("some_thing_else",
				adaptor.getNakamuraGroupId("apps:sakaioae:adhoc:courses:some:thing:else"));

	}

	public void testGetNakamuraNameCourseStructure(){
		String pattern = "([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)$";
		String template = "g[0] + '_' + g[1] + '_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5]";
		adaptor.setPattern(Pattern.compile(pattern));
		adaptor.setNakamuraIdTemplate(template);
		assertEquals("acad_term_subject_catalog_session_section",
				adaptor.getNakamuraGroupId("apps:sakaioae:provisioned:courses:acad:term:subject:catalog:session:section"));
	}

	public void testGetNakamuraNameNYU(){

		String pattern =  "([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)";
		String template = "'course_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5] + '_' + g[1] + '_' + g[6]";

		adaptor.setPattern(Pattern.compile(pattern));
		adaptor.setNakamuraIdTemplate(template);
		adaptor.setProvisionedStem("app:atlas:provisioned:groups");
		adaptor.setAdhocStem("apps:sakaioae:adhoc:courses");

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
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adaptor.getNakamuraGroupId("app:atlas:provisioned:groups:GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));
	}
}
