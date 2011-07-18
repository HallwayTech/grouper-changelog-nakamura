package org.sakaiproject.nakamura.grouper.changelog;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UnsupportedGroupException;
import org.sakaiproject.nakamura.grouper.changelog.util.StaticInitialGroupPropertiesProvider;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GroupType;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;

/**
 * Provision Simple Groups, Courses, and memberships to Sakai OAE.
 */
public class NakamuraEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = GrouperUtil.getLog(NakamuraEsbConsumer.class);

	// The interface to the SakaiOAE/nakamura server.
	private HttpSimpleGroupAdapter simpleGroupAdapter;

	// The interface to the SakaiOAE/nakamura server.
	private HttpCourseAdapter courseGroupAdapter;

	// Authenticated session for the Grouper API
	private GrouperSession grouperSession;

	// This job will try to process events for groups in these stems
	private Set<String> supportedStems;

	private static final String ADD_INCLUDE_EXCLUDE = "addIncludeExclude";
	private static final String CREATE_COURSE_ROLE = "student";
	private static final String SYSTEM_OF_RECORD_SUFFIX = "_systemOfRecord";

	// Configuration values from conf/grouper-loader.properties
	public static final String PROP_URL = NakamuraUtils.PROPERTY_KEY_PREFIX + ".url";
	public static final String PROP_USERNAME = NakamuraUtils.PROPERTY_KEY_PREFIX + ".username";
	public static final String PROP_PASSWORD = NakamuraUtils.PROPERTY_KEY_PREFIX + ".password";
	public static final String PROP_SUPPORTED_STEMS = NakamuraUtils.PROPERTY_KEY_PREFIX + ".supported.stems";

	public NakamuraEsbConsumer(){
		super();

        Builder<String,Object> config = new ImmutableMap.Builder<String, Object>();

		// Read and parse the settings.
		simpleGroupAdapter = new HttpSimpleGroupAdapter();
		courseGroupAdapter = new HttpCourseAdapter();

		config.put(PROP_URL, GrouperLoaderConfig.getPropertyString(PROP_URL, true));
		config.put(PROP_USERNAME, GrouperLoaderConfig.getPropertyString(PROP_USERNAME, true));
		config.put(PROP_PASSWORD, GrouperLoaderConfig.getPropertyString(PROP_PASSWORD, true));

		String supportedStemsConfig = GrouperLoaderConfig.getPropertyString(PROP_SUPPORTED_STEMS, true);
		supportedStems = new HashSet<String>();
		for (String stem : StringUtils.split(supportedStemsConfig, ",")){
			supportedStems.add(stem.trim());
		}
		config.put(PROP_SUPPORTED_STEMS, supportedStems);

		Map<String,Object> configMap = config.build();
		simpleGroupAdapter.updated(configMap);
		simpleGroupAdapter.setInitialPropertiesProvider(new StaticInitialGroupPropertiesProvider());
		simpleGroupAdapter.setGroupIdAdapter(new SimpleGroupIdAdapter());

		courseGroupAdapter.updated(configMap);
		courseGroupAdapter.setGroupIdAdapter(new TemplateGroupIdAdapter());
	}

	/**
	 * @see edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase#processChangeLogEntries(List, ChangeLogProcessorMetadata)
	 */
	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		long currentId = -1;

		// try catch so we can track that we made some progress
		try {
			for (ChangeLogEntry changeLogEntry : changeLogEntryList) {
				currentId = changeLogEntry.getSequenceNumber();

				if (log.isDebugEnabled()){
					log.info("Processing changelog entry=" + currentId);
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_ADD.name);

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_ADD + ": name=" + groupName);
					}
					checkSupportedGroup(groupName);
					Group group = GroupFinder.findByName(getGrouperSession(), groupName, false);

					if (group != null){
						if (NakamuraUtils.isCourseGroup(group)){
							for (GroupType groupType: group.getTypes()){
								// Create the OAE Course objects when the student_systemOfRecord group is created.
								// That group has the group type addIncludeExclude
								if (groupType.getName().equals(ADD_INCLUDE_EXCLUDE) &&
										group.getExtension().equals(CREATE_COURSE_ROLE + SYSTEM_OF_RECORD_SUFFIX)){
									courseGroupAdapter.createGroup(group);
								}
							}
						}

						if (NakamuraUtils.isSimpleGroup(group)){
							simpleGroupAdapter.createGroup(group);
						}
					}
					else {
						log.error("Group added event received for a group that doesn't exist? " + groupName);
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.id);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_DELETE.name);

					if (log.isDebugEnabled()){
						log.debug(ChangeLogTypeBuiltin.GROUP_DELETE + ": name=" + groupName);
					}
					checkSupportedGroup(groupName);
					Group group = GroupFinder.findByName(getGrouperSession(), groupName, false);
					if (group == null){
						if (NakamuraUtils.isSimpleGroup(group)){
							simpleGroupAdapter.deleteGroup(groupId, groupName);
						}
						if (groupName.endsWith(CREATE_COURSE_ROLE + SYSTEM_OF_RECORD_SUFFIX)){
							courseGroupAdapter.deleteGroup(groupId, groupName);
						}
					}
					else {
						log.error("Received a delete event for a group that still exists!");
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_UPDATE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.id);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.name);
					String propertyName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.propertyChanged);
					String oldValue = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.propertyOldValue);
					String newValue = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.GROUP_UPDATE.propertyNewValue);
					checkSupportedGroup(groupName);
					// TODO implement updateProperty
					// nakamuraGroupAdapter.updateProperty(groupId, propertyName, oldValue, newValue);
					log.debug("Group update, name: "  + groupId + ", property: " + propertyName
							+ ", from: '" + oldValue + "', to: '" + newValue + "'");
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupId);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);

					if (member != null && "person".equals(member.getTypeName()) ){
						log.debug("Membership add, group: " + groupName + " subjectId: " + memberId);
						checkSupportedGroup(groupName);

						if (NakamuraUtils.isCourseGroup(groupName)){
							courseGroupAdapter.addMembership(groupId, groupName, memberId);
						}
						else if (NakamuraUtils.isSimpleGroup(groupName)){
							simpleGroupAdapter.addMembership(groupId, groupName, memberId);
						}
					}
				}

				if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
					String groupId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupId);
					String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.groupName);
					String memberId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
					Subject member = SubjectFinder.findByIdentifier(memberId, false);

					if (member != null && "person".equals(member.getTypeName()) ){
						log.debug("Membership delete, group: " + groupName + " subjectId: " + memberId);
						checkSupportedGroup(groupName);

						if (NakamuraUtils.isCourseGroup(groupName)){
							courseGroupAdapter.addMembership(groupId, groupName, memberId);
						}
						else if (NakamuraUtils.isSimpleGroup(groupName)){
							simpleGroupAdapter.addMembership(groupId, groupName, memberId);
						}
					}
				}
				// we successfully processed this record
			}
		}
		catch(UnsupportedGroupException e){
			log.error(e.getMessage());
		}
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
	 * Does the group name fall inside of the stems we're configured to keep
	 * in sync with sakai?
	 * @param groupName
	 * @throws UnsupportedGroupException
	 */
	private void checkSupportedGroup(String groupName) throws UnsupportedGroupException {
		boolean supported = false;
		for (String stem: supportedStems){
			if (groupName.startsWith(stem)) {
				supported = true;
				break;
			}
		}
		if (!supported){
			throw new UnsupportedGroupException("Not configured to handle " + groupName + ". Check the elfilter.");
		}
	}

	/**
	 * Lazy-load the grouperSession
	 * @return
	 */
	private GrouperSession getGrouperSession(){
		if ( grouperSession == null) {
			try {
				grouperSession = GrouperSession.start(SubjectFinder.findRootSubject(), false);
				log.debug("started session: " + this.grouperSession);
			}
			catch (SessionException se) {
				throw new GrouperException("Error starting session: " + se.getMessage(), se);
			}
		}
		return grouperSession;
	}

	public void setNakamuraGroupAdapter(HttpSimpleGroupAdapter nakamuraGroupAdapter) {
		this.simpleGroupAdapter = nakamuraGroupAdapter;
	}
}