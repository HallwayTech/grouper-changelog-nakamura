package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class GroupIdAdapterImplTestCase extends TestCase {

	private Set<String> pseudoGroupSuffixes = ImmutableSet.of("student", "ta", "lecturer", "manager", "members");
	private Set<String> includeExcludeSuffixes = ImmutableSet.of("_includes", "_excludes", "_systemOfRecord", "_systemOfRecordAndIncludes");
	private String roleMappings = "TAs:ta, lecturers:lecturer, students:student, managers:manager";

	private String ADHOC_SIMPLEGROUPS_STEM = "apps:sakaioae:adhoc:simplegroups";
	private String ADHOC_COURSEGROUPS_STEM = "apps:sakaioae:adhoc:groups";
	private String PROV_SIMPLEGROUPS_STEM = "apps:sakaioae:provisioned:simplegroups";
	private String PROV_COURSEGROUPS_STEM = "apps:sakaioae:provisioned:groups";
	private String INST_SIMPLEGROUPS_STEM = "inst:sis:simplegroups";
	private String INST_COURSEGROUPS_STEM = "inst:sis:groups";

	GroupIdAdapterImpl adapter;
	TemplateGroupIdAdapter tmplAdapter;
	SimpleGroupIdAdapter simpleAdapter;

	@Override
	public void setUp(){
		simpleAdapter = new SimpleGroupIdAdapter();
		simpleAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		simpleAdapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		simpleAdapter.setRoleMap(roleMappings);

		tmplAdapter = new TemplateGroupIdAdapter();
		String pattern =  "([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)";
		String template = "'course_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5] + '_' + g[1] + '_' + g[6]";
		tmplAdapter.setPattern(Pattern.compile(pattern));
		tmplAdapter.setNakamuraIdTemplate(template);
		tmplAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		tmplAdapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		tmplAdapter.setRoleMap(roleMappings);

		adapter = new GroupIdAdapterImpl();
		adapter.setSimpleGroupIdAdapter(simpleAdapter);
		adapter.setTemplateGroupIdAdapter(tmplAdapter);
		adapter.setAdhocCourseGroupsStem(ADHOC_COURSEGROUPS_STEM);
		adapter.setAdhocSimpleGroupsStem(ADHOC_SIMPLEGROUPS_STEM);
		adapter.setProvisionedCourseGroupsStem(PROV_COURSEGROUPS_STEM);
		adapter.setProvisionedSimpleGroupsStem(PROV_SIMPLEGROUPS_STEM);
		adapter.setInstitutionalCourseGroupsStem(INST_COURSEGROUPS_STEM);
		adapter.setInstitutionalSimpleGroupsStem(INST_SIMPLEGROUPS_STEM);
	}

	public void testNullGroupId(){
		assertEquals(null, adapter.getGroupId(null));
	}

	public void testGetProvisionedCourseGroupId(){
		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer"));

		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adapter.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));
	}

	public void testGetAdhocCourseGroupId(){
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-members",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-student",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-ta",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-lecturer",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer"));

		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-members",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-student",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-ta",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-ta",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-lecturer",
				adapter.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));
	}

	public void testGetInstitutionalCourseId(){

		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer"));

		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adapter.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));
	}

	public void testGetProvisionedSimpleGroupId(){
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members"));
		assertEquals("simpler0_grouper0-manager",
				adapter.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:manager"));

		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_includes"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecordAndIncludes"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_excludes"));
	}

	public void testGetAdhocSimpleGroupId(){
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members"));
		assertEquals("simpler0_grouper0-manager",
				adapter.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:manager"));

		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_includes"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecordAndIncludes"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_excludes"));
	}

	public void testGetInstitutionalSimpleGroupId(){
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members"));
		assertEquals("simpler0_grouper0-manager",
				adapter.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:manager"));

		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_includes"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecordAndIncludes"));
		assertEquals("simpler0_grouper0-members",
				adapter.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_excludes"));
	}

	public void testIsProvisioned(){
		assertFalse(adapter.isProvisioned(null));
		assertTrue(adapter.isProvisioned(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(adapter.isProvisioned(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isProvisioned(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isProvisioned(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isProvisioned(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isProvisioned(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}


	public void testIsAdhoc(){
		assertFalse(adapter.isAdhoc(null));
		assertTrue(adapter.isAdhoc(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(adapter.isAdhoc(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isAdhoc(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isAdhoc(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isAdhoc(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isAdhoc(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));
	}

	public void testIsInstitutional(){
		assertFalse(adapter.isInstitutional(null));
		assertTrue(adapter.isInstitutional(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(adapter.isInstitutional(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isInstitutional(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isInstitutional(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isInstitutional(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isInstitutional(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}

	public void testIsCourseGroup(){
		assertFalse(adapter.isCourseGroup(null));
		assertTrue(adapter.isCourseGroup(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isCourseGroup(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertTrue(adapter.isCourseGroup(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isCourseGroup(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertTrue(adapter.isCourseGroup(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(adapter.isCourseGroup(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}

	public void testIsSimpleGroup(){
		assertFalse(adapter.isSimpleGroup(null));
		assertFalse(adapter.isSimpleGroup(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(adapter.isSimpleGroup(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isSimpleGroup(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(adapter.isSimpleGroup(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(adapter.isSimpleGroup(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(adapter.isSimpleGroup(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}
}
