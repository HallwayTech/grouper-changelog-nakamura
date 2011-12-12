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
package org.sakaiproject.nakamura.grouper.changelog.esb;

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.support.membermodification.MemberMatcher.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;

import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.util.GrouperUtil;


@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {GrouperUtil.class, GroupFinder.class, GrouperSession.class })
public class RestrictedCourseEsbConsumerTest extends TestCase {

	private RestrictedCourseGroupEsbConsumer consumer;
	private NakamuraManager nakamuraManager;
	private GroupIdManagerImpl groupIdManager;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	private static final String[] ENABLED_STEMS = new String[] {
		"edu:apps:sakai:courses:FA11:SOMETHING.*",
		"inst:sis:courses:.*",
		"edu:apps:sakai:courses:FA11:SOMETHING:ELSE.*",
	};

	public void setUp(){
		suppress(method(GrouperUtil.class, "getLog"));
		metadata = mock(ChangeLogProcessorMetadata.class);
		entry = mock(ChangeLogEntry.class);

		nakamuraManager = mock(NakamuraManager.class);
		groupIdManager = mock(GroupIdManagerImpl.class);

		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");
		consumer = new RestrictedCourseGroupEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdManager = groupIdManager;
		consumer.configurationLoaded = true;

		List<String> enabledStems = Arrays.asList(ENABLED_STEMS);
		consumer.setEnabledStems(enabledStems);
	}

	public void testSortedByLength(){
		List<String> toSort = Arrays.asList(ENABLED_STEMS);
		consumer.sortByLengthDesc(toSort);
		assertEquals(ENABLED_STEMS.length, toSort.size());
		assertEquals("edu:apps:sakai:courses:FA11:SOMETHING:ELSE.*",toSort.get(0));
		assertEquals("edu:apps:sakai:courses:FA11:SOMETHING.*",toSort.get(1));
		assertEquals("inst:sis:courses:.*",toSort.get(2));
	}

	private void prepEntry(String grouperName){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name)).thenReturn(grouperName).thenReturn(grouperName);
		when(groupIdManager.getWorldType(grouperName)).thenReturn(GroupIdManagerImpl.COURSE);
		when(groupIdManager.isInstitutional(grouperName)).thenReturn(false);
	}

	public void testDontIgnoreInRestrictionTable(){
		String grouperName = "edu:apps:sakai:courses:FA11:SOMETHING:coursename:rolename";
		prepEntry(grouperName);
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotInRestrictionTable(){
		String[] invalidNames = new String[] {"edu:apps:sakai:courses:FA11", "edu", "inst:sis", "unknown", "", null};
		for (String invalid : invalidNames){
			prepEntry(invalid);
			assertTrue(consumer.ignoreChangelogEntry(entry));
		}
	}
}