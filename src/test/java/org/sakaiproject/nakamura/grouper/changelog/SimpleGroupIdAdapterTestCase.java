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

import junit.framework.TestCase;

import org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer;

import com.google.common.collect.ImmutableSet;

public class SimpleGroupIdAdapterTestCase extends TestCase  {

	private Set<String> pseudoGroupSuffixes = ImmutableSet.of("member", "manager");
	private Set<String> includeExcludeSuffixes = ImmutableSet.of("_includes", "_excludes", "_systemOfRecord", "_systemOfRecordAndIncludes");
	private String roleMappings = "TAs:ta, lecturers:lecturer, students:student, managers:manager";

	SimpleGroupIdAdapter adapter;

	@Override
	public void setUp(){
		adapter = new SimpleGroupIdAdapter();
		adapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		adapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		adapter.setRoleMap(roleMappings);
	}

	public void testGetNakamuraIdMembers(){
		assertEquals("newgroup_some_thing_else-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX,
				adapter.getGroupId("newgroup:some:thing:else:" + SimpleGroupEsbConsumer.MEMBER_SUFFIX));
		assertEquals("newgroup_some_thing_else-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX,
				adapter.getGroupId("newgroup:some:thing:else:" + SimpleGroupEsbConsumer.MEMBER_SUFFIX + "_systemOfRecord"));
	}

	public void testGetNakamuraIdManagers(){
		assertEquals("newgroup_some_thing_else-" + SimpleGroupEsbConsumer.MANAGER_SUFFIX,
				adapter.getGroupId("newgroup:some:thing:else:" + SimpleGroupEsbConsumer.MANAGER_SUFFIX));
	}

	public void testGetNakamuraIdParent(){
		assertEquals("newgroup_some_thing_else_sssss", adapter.getGroupId("newgroup:some:thing:else:sssss"));
	}
}
