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
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseGroupNakamuraManagerImpl;
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

	private CourseTitleEsbConsumer consumer;
	private HttpCourseGroupNakamuraManagerImpl nakamuraManager;
	private GroupIdAdapterImpl groupIdAdapter;
	private ChangeLogProcessorMetadata metadata;
	private ChangeLogEntry entry;

	private static final String VALID_STEM = "edu:apps:sakaioae:courses:some:stem";
	private static final String INVALID_STEM = "edu:apps:sakaioae:courses:some:stem:extra";

	public void setUp(){
		nakamuraManager = mock(HttpCourseGroupNakamuraManagerImpl.class);
		groupIdAdapter = mock(GroupIdAdapterImpl.class);
		metadata = mock(ChangeLogProcessorMetadata.class);
		when(metadata.getConsumerName()).thenReturn("UnitTestConsumer");

		consumer = new CourseTitleEsbConsumer();
		consumer.setGroupManager(nakamuraManager);
		consumer.setGroupIdAdapter(groupIdAdapter);
		consumer.setConfigurationLoaded(true);
		consumer.setSectionStemPattern(Pattern.compile("edu:apps:sakaioae:courses:([^:]+):([^:]+)"));
		suppress(method(GrouperUtil.class, "getLog"));

		entry = mock(ChangeLogEntry.class);
	}

	public void testIgnoreInvalidEntryType() throws Exception{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)).thenReturn(false);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreDoesntMatchStemPattern(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name)).thenReturn(INVALID_STEM);
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testIgnoreNotADescriptionChange(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name)).thenReturn(VALID_STEM);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyChanged)).thenReturn("notdescription");
		assertTrue(consumer.ignoreChangelogEntry(entry));
	}

	public void testDontIgnore(){
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name)).thenReturn(VALID_STEM);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyChanged)).thenReturn("description");
		assertFalse(consumer.ignoreChangelogEntry(entry));
	}

	public void testAddTitle() throws GroupModificationException{
		when(entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)).thenReturn(true);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name)).thenReturn(VALID_STEM);
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyChanged)).thenReturn("description");
		when(entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyNewValue)).thenReturn("newdescription");
		when(nakamuraManager.groupExists("some_course")).thenReturn(true);
		when(groupIdAdapter.getGroupId(VALID_STEM + ":students")).thenReturn("some_course-student");
		when(groupIdAdapter.getPseudoGroupParent("some_course-student")).thenReturn("some_course");
		assertFalse(consumer.ignoreChangelogEntry(entry));

		// Prevent GrouperLoaderConfig from staticing the test up
		consumer.setConfigurationLoaded(true);
		consumer.processChangeLogEntries(ImmutableList.of(entry), metadata);

		verify(nakamuraManager).setProperty("some_course", CourseTitleEsbConsumer.COURSE_TITLE_PROPERTY, "newdescription");
	}
}