package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.HttpCourseAdapter;
import org.sakaiproject.nakamura.grouper.changelog.SimpleGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.TemplateGroupIdAdapter;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;

/**
 * Update course titles in Sakai OAE when the description is updated on a stem.
 * The stem will be the last stem in the path to the role groups for that course.
 */
public class CourseTitleEsbConsumer extends BaseGroupEsbConsumer {

	private static Log log = LogFactory.getLog(CourseTitleEsbConsumer.class);

	// Regex to determine if this is a section stem
	private Pattern sectionStemPattern;

	// The interface to the SakaiOAE/nakamura server.
	private HttpCourseAdapter groupAdapter;

	// Convert grouper names to nakamura group ids
	protected GroupIdAdapterImpl groupIdAdapter;

	private boolean configurationLoaded = false;

	// Configuration
	public static final String PROP_SECTION_STEM_REGEX = "section.stem.regex";

	public static final String COURSE_TITLE_PROPERTY = "sakai:group-title";

	protected void loadConfiguration(String consumerName) {
		if (configurationLoaded){
			return;
		}
		super.loadConfiguration(consumerName);

		String cfgPrefix = BaseGroupEsbConsumer.CONFIG_PREFIX + "." + consumerName + ".";
		sectionStemPattern = Pattern.compile(
				GrouperLoaderConfig.getPropertyString(
						cfgPrefix + PROP_SECTION_STEM_REGEX, true));

		SimpleGroupIdAdapter simpleAdapter = new SimpleGroupIdAdapter();
		simpleAdapter.loadConfiguration(consumerName);

		TemplateGroupIdAdapter tmplAdapter = new TemplateGroupIdAdapter();
		tmplAdapter.loadConfiguration(consumerName);

		groupIdAdapter = new GroupIdAdapterImpl();
		groupIdAdapter.loadConfiguration(consumerName);
		groupIdAdapter.setSimpleGroupIdAdapter(simpleAdapter);
		groupIdAdapter.setTemplateGroupIdAdapter(tmplAdapter);

		groupAdapter = new HttpCourseAdapter();
		groupAdapter.setUrl(url);
		groupAdapter.setUsername(username);
		groupAdapter.setPassword(password);
		groupAdapter.setGroupIdAdapter(groupIdAdapter);
		groupAdapter.setCreateUsers(createUsers);
		groupAdapter.setDryrun(dryrun);
		groupAdapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
	}

	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		String consumerName = changeLogProcessorMetadata.getConsumerName();
		loadConfiguration(consumerName);
		
		int entryCount = changeLogEntryList.size();
		log.info("Received a batch of " + entryCount + " entries : " +
				changeLogEntryList.get(0).getSequenceNumber() + " - " +
				changeLogEntryList.get(entryCount - 1).getSequenceNumber());
		
		long currentId = -1;

		// try catch so we can track that we made some progress
		try {
			for (ChangeLogEntry entry : changeLogEntryList) {
				currentId = entry.getSequenceNumber();
				log.info("Processing changelog entry=" + currentId);

				if (ignoreChangelogEntry(entry)){
					continue;
				}

				String stemName = entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name);
				log.info("Start STEM_UPDATE : " + stemName);
				String parentGroupId = groupIdAdapter.getPseudoGroupParent(groupIdAdapter.getGroupId(stemName + ":students"));

				if (parentGroupId != null){
					String description = entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyNewValue);
					groupAdapter.setProperty(parentGroupId, COURSE_TITLE_PROPERTY, description);
				}
				log.info("Finished STEM_UPDATE : " + stemName);
			}

			log.info("Finished the batch of " + entryCount + " entries : " +
					changeLogEntryList.get(0).getSequenceNumber() + " - " +
					changeLogEntryList.get(entryCount - 1).getSequenceNumber());
		}
		// Stop processing changelog entries.
		catch (Exception e) {
			changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
			// we made it to this -1
			return currentId - 1;
		}

		if (currentId == -1) {
			log.error("Didn't process any records.");
			throw new RuntimeException("Couldn't process any records");
		}

		return currentId;
	}

	/**
	 * @param entry a change log entry
	 * @return whether or not to ignore this entry
	 */
	protected boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = false;

		if(!entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.STEM_UPDATE)){
			log.info("Entry was not a stem update.");
			return true;
		}

		String stemName = entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.name);

		// The regex exnsures we only process stems at the section level
		if (!sectionStemPattern.matcher(stemName).matches()){
			log.info("Updated stem did not match the " + PROP_SECTION_STEM_REGEX + " : " + stemName);
			ignore = true;
		}

		// We only change the title for description updates
		String propChanged = entry.retrieveValueForLabel(ChangeLogLabels.STEM_UPDATE.propertyChanged);
		if (!"description".equals(propChanged)){
			ignore = true;
		}

		return ignore;
	}

	public void setSectionStemPattern(Pattern sectionStemPattern) {
		this.sectionStemPattern = sectionStemPattern;
	}

	public void setGroupAdapter(HttpCourseAdapter groupAdapter) {
		this.groupAdapter = groupAdapter;
	}

	public void setGroupIdAdapter(GroupIdAdapterImpl groupIdAdapter) {
		this.groupIdAdapter = groupIdAdapter;
	}

	public void setConfigurationLoaded(boolean configLoaded){
		this.configurationLoaded = configLoaded;
	}
}