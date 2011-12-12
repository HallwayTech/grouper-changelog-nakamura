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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.support.membermodification.MemberMatcher.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;

import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdManagerImpl;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;

import com.google.common.collect.ImmutableList;

import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.util.GrouperUtil;


@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {GrouperUtil.class, GroupFinder.class, GrouperSession.class })
public class CourseTitleEsbConsumerTest extends TestCase {

	private static final String VALID_STEM = "edu:apps:sakaioae:courses:some:stem";
	private static final String INVALID_STEM = "edu:apps:sakaioae:courses:some:stem:extra";

	private WorldTitleEsbConsumer consumer;
	private NakamuraManager nakamuraManager;
	private GroupIdManagerImpl groupIdManager;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	public void setUp(){
		suppress(method(GrouperUtil.class, "getLog"));

		nakamuraManager = mock(NakamuraManager.class);
		groupIdManager = mock(GroupIdManagerImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		entry = mock(ChangeLogEntry.class);

		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name)).thenReturn(VALID_STEM);
		when(entry.getSequenceNumber()).thenReturn((long)10);

		consumer = new WorldTitleEsbConsumer();
		consumer.nakamuraManager = nakamuraManager;
		consumer.groupIdManager = groupIdManager;
		consumer.configurationLoaded = true;
		consumer.sectionStemPattern = Pattern.compile("edu:apps:sakaioae:courses:([^:]+):([^:]+)");
	}

	public void testIgnoreInvalidEntryType() throws Exception{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)).thenReturn(false);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreDoesntMatchStemPattern(){

		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name)).thenReturn(INVALID_STEM);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotADescriptionChange(){
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyChanged)).thenReturn("notdescription");
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyChanged)).thenReturn("description");
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testAddTitle() throws GroupModificationException{
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyChanged)).thenReturn("description");
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyNewValue)).thenReturn("newdescription");
		when(nakamuraManager.groupExists("some_course")).thenReturn(true);
		when(groupIdManager.getGroupId(VALID_STEM + ":students")).thenReturn("some_course-student");
		when(groupIdManager.getWorldId("some_course-student")).thenReturn("some_course");
		assertFalse(consumer.ignoreChangelogEntry(entry));

		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);
		verify(nakamuraManager).setProperty("some_course", WorldTitleEsbConsumer.COURSE_TITLE_PROPERTY, "newdescription");
	}
}