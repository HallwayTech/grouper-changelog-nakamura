package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class GroupIdManagerTestCase extends TestCase {

	private Set<String> pseudoGroupSuffixes = ImmutableSet.of("student", "ta", "lecturer", "manager", "members");
	private Set<String> includeExcludeSuffixes = ImmutableSet.of("_includes", "_excludes", "_systemOfRecord", "_systemOfRecordAndIncludes");
	private String roleMappings = "TAs:ta, lecturers:lecturer, students:student, managers:manager";

	private String ADHOC_SIMPLEGROUPS_STEM = "apps:sakaioae:adhoc:simplegroups";
	private String ADHOC_COURSEGROUPS_STEM = "apps:sakaioae:adhoc:groups";
	private String PROV_SIMPLEGROUPS_STEM = "apps:sakaioae:provisioned:simplegroups";
	private String PROV_COURSEGROUPS_STEM = "apps:sakaioae:provisioned:groups";
	private String INST_SIMPLEGROUPS_STEM = "inst:sis:simplegroups";
	private String INST_COURSEGROUPS_STEM = "inst:sis:groups";

	GroupIdManagerImpl manager;
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
		tmplAdapter.pattern = Pattern.compile(pattern);
		tmplAdapter.nakamuraIdTemplate = template;
		tmplAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		tmplAdapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		tmplAdapter.setRoleMap(roleMappings);

		manager = new GroupIdManagerImpl();
		manager.setSimpleGroupIdAdapter(simpleAdapter);
		manager.setTemplateGroupIdAdapter(tmplAdapter);
		manager.setAdhocCourseGroupsStem(ADHOC_COURSEGROUPS_STEM);
		manager.setAdhocSimpleGroupsStem(ADHOC_SIMPLEGROUPS_STEM);
		manager.setProvisionedCourseGroupsStem(PROV_COURSEGROUPS_STEM);
		manager.setProvisionedSimpleGroupsStem(PROV_SIMPLEGROUPS_STEM);
		manager.setInstitutionalCourseGroupsStem(INST_COURSEGROUPS_STEM);
		manager.setInstitutionalSimpleGroupsStem(INST_SIMPLEGROUPS_STEM);
	}

	public void testNullGroupId(){
		assertEquals(null, manager.getGroupId(null));
	}

	public void testGetProvisionedCourseGroupId(){
		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer"));

		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				manager.getGroupId(PROV_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));
	}

	public void testGetAdhocCourseGroupId(){
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-members",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-student",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-ta",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-lecturer",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer"));

		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-members",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-student",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-ta",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-ta",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("GRAD_FA11_MATH-GA_1410_1_001-lecturer",
				manager.getGroupId(ADHOC_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));
	}

	public void testGetInstitutionalCourseId(){

		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer"));

		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				manager.getGroupId(INST_COURSEGROUPS_STEM + ":GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));
	}

	public void testGetProvisionedSimpleGroupId(){
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members"));
		assertEquals("simpler0_grouper0-manager",
				manager.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:manager"));

		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_includes"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecordAndIncludes"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(PROV_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_excludes"));
	}

	public void testGetAdhocSimpleGroupId(){
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members"));
		assertEquals("simpler0_grouper0-manager",
				manager.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:manager"));

		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_includes"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecordAndIncludes"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(ADHOC_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_excludes"));
	}

	public void testGetInstitutionalSimpleGroupId(){
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members"));
		assertEquals("simpler0_grouper0-manager",
				manager.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:manager"));

		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_includes"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecord"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_systemOfRecordAndIncludes"));
		assertEquals("simpler0_grouper0-members",
				manager.getGroupId(INST_SIMPLEGROUPS_STEM + ":simpler0:grouper0:members_excludes"));
	}

	public void testIsProvisioned(){
		assertFalse(manager.isProvisioned(null));
		assertTrue(manager.isProvisioned(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(manager.isProvisioned(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isProvisioned(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isProvisioned(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isProvisioned(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isProvisioned(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}


	public void testIsAdhoc(){
		assertFalse(manager.isAdhoc(null));
		assertTrue(manager.isAdhoc(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(manager.isAdhoc(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isAdhoc(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isAdhoc(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isAdhoc(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isAdhoc(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));
	}

	public void testIsInstitutional(){
		assertFalse(manager.isInstitutional(null));
		assertTrue(manager.isInstitutional(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(manager.isInstitutional(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isInstitutional(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isInstitutional(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isInstitutional(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isInstitutional(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}

	public void testIsCourseGroup(){
		assertFalse(manager.isCourseGroup(null));
		assertTrue(manager.isCourseGroup(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isCourseGroup(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertTrue(manager.isCourseGroup(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isCourseGroup(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertTrue(manager.isCourseGroup(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertFalse(manager.isCourseGroup(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}

	public void testIsSimpleGroup(){
		assertFalse(manager.isSimpleGroup(null));
		assertFalse(manager.isSimpleGroup(INST_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(manager.isSimpleGroup(INST_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isSimpleGroup(PROV_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(manager.isSimpleGroup(PROV_SIMPLEGROUPS_STEM + ":some:group:role"));

		assertFalse(manager.isSimpleGroup(ADHOC_COURSEGROUPS_STEM + ":some:group:role"));
		assertTrue(manager.isSimpleGroup(ADHOC_SIMPLEGROUPS_STEM + ":some:group:role"));
	}

	public void testGetAllGroup(){
		assertEquals(INST_COURSEGROUPS_STEM + ":X:all",manager.getAllGroup(INST_COURSEGROUPS_STEM + ":X:students"));
		assertEquals(INST_SIMPLEGROUPS_STEM + ":X:all", manager.getAllGroup(INST_SIMPLEGROUPS_STEM + ":X:students"));

		assertEquals(ADHOC_COURSEGROUPS_STEM + ":X:all", manager.getAllGroup(ADHOC_COURSEGROUPS_STEM + ":X:students"));
		assertEquals(ADHOC_SIMPLEGROUPS_STEM + ":X:all", manager.getAllGroup(ADHOC_SIMPLEGROUPS_STEM + ":X:students"));

		assertEquals(PROV_COURSEGROUPS_STEM + ":X:all", manager.getAllGroup(PROV_COURSEGROUPS_STEM + ":X:students"));
		assertEquals(PROV_SIMPLEGROUPS_STEM + ":X:all", manager.getAllGroup(PROV_SIMPLEGROUPS_STEM + ":X:students"));
	}

	public void testToProvisioned(){
		assertEquals(PROV_COURSEGROUPS_STEM + ":X:students",manager.toProvisioned(PROV_COURSEGROUPS_STEM + ":X:students"));
		assertEquals(PROV_SIMPLEGROUPS_STEM + ":X:students", manager.toProvisioned(PROV_SIMPLEGROUPS_STEM + ":X:students"));

		assertEquals(null, manager.toProvisioned(ADHOC_COURSEGROUPS_STEM + ":X:students"));
		assertEquals(null, manager.toProvisioned(ADHOC_SIMPLEGROUPS_STEM + ":X:students"));

		assertEquals(PROV_COURSEGROUPS_STEM + ":X:students", manager.toProvisioned(INST_COURSEGROUPS_STEM + ":X:students"));
		assertEquals(PROV_SIMPLEGROUPS_STEM + ":X:students", manager.toProvisioned(INST_SIMPLEGROUPS_STEM + ":X:students"));
	}
}
