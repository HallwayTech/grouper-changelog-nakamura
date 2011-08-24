package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.util.ChangeLogUtils;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.app.loader.db.GrouperLoaderDb;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;

/**
 * Provision and sync Course Groups in Sakai OAE with Grouper.
 * Restrict the group events that will be sent to Sakai OAE with a DB table.
 *
 * This class is pretty specific to NYU's implementation. They have a table
 * called COURSE_RESTRICTIONS(RESTRICTION, APP1, APP2, ...)
 *
 * Each row has a RESTRICTION which is a LIKE expression to match grouper names
 * and a boolean flag to indicate if APPX is enabled for that stem
 *
 * RESTRICTION,          SIS, SAKAIOAE, UNKNOWN_APPX
 * inst:sis:courses%,     1, 1, 0
 * app:sakaioae:courses%, 0, 1, 0
 *
 * We query this table where SAKAIOAE=1 and then sort the expressions in REVERSE
 * order of their length. In that way we match more specific restrictions before
 * more general expressions.
 */
public class RestrictedCourseGroupEsbConsumer extends CourseGroupEsbConsumer {

	private static final String PROP_RESTRICTION_QUERY = "restriction.query";
	private static final String PROP_DB_PROFILE = "db.profile";

	private static final String DEFAULT_DB_PROFILE = "warehouse";

	private static Log log = LogFactory.getLog(RestrictedCourseGroupEsbConsumer.class);

	// The regexes to match to the grouperName
	private List<Pattern> enabledStems;

	private String restrictionQuery;
	private String dbProfile;

	/**
	 * Load up the configuration necessary to act on {@link ChangeLogEntry} objects.
	 */
	protected void loadConfiguration(String consumerName) {
		if (configurationLoaded){
			return;
		}
		super.loadConfiguration(consumerName);
		try {
			String cfgPrefix = "changeLog.consumer." + consumerName + ".";
			restrictionQuery = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_RESTRICTION_QUERY, true);
			dbProfile = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_DB_PROFILE, DEFAULT_DB_PROFILE);
			loadRestrictionTable();
		}
		catch (SQLException sqle){
			throw new RuntimeException(sqle);
		}
	}

	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
			ChangeLogProcessorMetadata changeLogProcessorMetadata) {
		String consumerName = changeLogProcessorMetadata.getConsumerName();
		loadConfiguration(consumerName);
		return super.processChangeLogEntries(changeLogEntryList, changeLogProcessorMetadata);
	}

	/**
	 * Load the list of enabled stem patterns from the database.
	 * @throws SQLException
	 */
	private void loadRestrictionTable() throws SQLException{
		String atlasEnabledQuery = restrictionQuery;
		GrouperLoaderDb gldb = GrouperLoaderConfig.retrieveDbProfile(dbProfile);
		Connection conn = gldb.connection();
		PreparedStatement stmt = conn.prepareStatement(atlasEnabledQuery);
		ResultSet results = stmt.executeQuery();
		List<String> enabledStems = new ArrayList<String>();
		while (results.next()){
			enabledStems.add(results.getString(0).replaceAll("%", "\\.\\*"));
		}
		if (!enabledStems.isEmpty()){
			log.debug("Loaded the enabled stems from the restriction table.");
			setEnabledStems(enabledStems);
		}
	}

	/**
	 * @return true if the entry should be ignored
	 */
	protected boolean ignoreChangelogEntry(ChangeLogEntry entry){
		boolean ignore = super.ignoreChangelogEntry(entry);
		if (ignore == false){;
			String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
			if(!isEnabled(grouperName)){
				log.debug(entry.getSequenceNumber() + " Ignoring because no match in the restrictions table : " + grouperName);
				ignore = true;
			}
		}
		return ignore;
	}

	/**
	 * Does the grouper name match one of the enabled stems?
	 * @param grouperName
	 * @return
	 */
	private boolean isEnabled(String grouperName){
		boolean isEnabled = false;
		if (grouperName != null){
			for(Pattern stemPattern: enabledStems){
				if(stemPattern.matcher(grouperName).matches()){
					isEnabled = true;
				}
			}
		}
		return isEnabled;
	}

	public List<Pattern> getEnabledStems(){
		return enabledStems;
	}

	/**
	 * Sort the list of enabled stems and compile them into {@link Pattern}s
	 * @param enabled
	 */
	public void setEnabledStems(List<String> enabled){
		enabledStems = new ArrayList<Pattern>();
		sortByLengthDesc(enabled);
		for(String e : enabled){
			enabledStems.add(Pattern.compile(e));
		}
		log.info("Loaded restrictions for Sakai OAE : " +
				StringUtils.join(enabledStems.toArray(), ","));
	}

	/**
	 * Sort String by length, longest first.
	 * @param toSort the {@link List} to be sorted
	 */
	public void sortByLengthDesc(List<String> toSort){
		Collections.sort(toSort,
				new Comparator<String>(){
					public int compare(String o1, String o2){
						if(o1.length() < o2.length()){
							return 1;
						}
						if(o1.length() > o2.length()){
							return -1;
						}
						return 0;
					}
				}
		);
	}
}