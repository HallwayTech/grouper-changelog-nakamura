/* Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class TemplateGroupIdAdapterTestCase extends TestCase {

	private Set<String> pseudoGroupSuffixes = ImmutableSet.of("student", "ta", "lecturer", "manager", "members");
	private Set<String> includeExcludeSuffixes = ImmutableSet.of("_includes", "_excludes", "_systemOfRecord", "_systemOfRecordAndIncludes");
	private String roleMappings = "TAs:ta, lecturers:lecturer, instructors:lecturer, students:student, managers:manager";

	TemplateGroupIdAdapter adaptor;

	@Override
	public void setUp(){
		adaptor = new TemplateGroupIdAdapter();
		adaptor.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		adaptor.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		adaptor.setRoleMap(roleMappings);

	}

	public void testGetNakamuraNameCourseStructure(){
		String pattern = "([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)$";
		String template = "g[0] + '_' + g[1] + '_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5]";
		adaptor.pattern = Pattern.compile(pattern);
		adaptor.nakamuraIdTemplate = template;
		assertEquals("acad_term_subject_catalog_session_section",
				adaptor.getGroupId("acad:term:subject:catalog:session:section"));
	}

	public void testGetCourseGroupId(){

		String pattern =  "([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)";
		String template = "'course_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5] + '_' + g[1] + '_' + g[6]";

		adaptor.pattern = Pattern.compile(pattern);
		adaptor.nakamuraIdTemplate = template;

		assertEquals(null, adaptor.getGroupId(null));
		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:members"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:student"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:ta"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:lecturer"));

		assertEquals("course_MATH-GA_1410_1_001_FA11-members",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:members_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-student",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:student_includes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecord"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-ta",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:ta_systemOfRecordAndIncludes"));
		assertEquals("course_MATH-GA_1410_1_001_FA11-lecturer",
				adaptor.getGroupId("GRAD:FA11:MATH-GA:1410:1:001:lecturer_excludes"));

		assertEquals("course_ELEC1-DC_1109_S_003_FA11-lecturer",
				adaptor.getGroupId("GRAD:FA11:ELEC1-DC:1109:S:003:instructors"));
	}
}
