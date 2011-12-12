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

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class AbstractGroupIdAdapterTest extends TestCase {

	private AbstractGroupIdAdapter gAdapter;

	@Override
	public void setUp(){
		gAdapter = new SimpleGroupIdAdapter();
		gAdapter.setPseudoGroupSuffixes(ImmutableSet.of("manager", "member", "student", "lecturer", "ta"));
	}

	public void testGetPseudoGroupParent(){
		assertEquals("some-group", gAdapter.getWorldId("some-group"));
		assertEquals("some-group", gAdapter.getWorldId("some-group-student"));

		assertEquals("some_group", gAdapter.getWorldId("some_group"));
		assertEquals("some_group", gAdapter.getWorldId("some_group-student"));
		assertEquals("some_group_student", gAdapter.getWorldId("some_group_student"));
	}
}
