package org.sakaiproject.nakamura.grouper.changelog;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraUtils;

import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;
import edu.internet2.middleware.grouper.util.GrouperUtil;

public class BaseGroupEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = GrouperUtil.getLog(BaseGroupEsbConsumer.class);

	public static final String PROPERTY_KEY_PREFIX = "nakamura";

	protected URL url;
	protected String username;
	protected String password;

	protected HashSet<String> includeExcludeSuffixes;
	protected TemplateGroupIdAdapter templateGroupIdAdapter;

	// Authenticated session for the Grouper API
	protected GrouperSession grouperSession;

	// Configuration values from conf/grouper-loader.properties
	public static final String PROP_URL =      PROPERTY_KEY_PREFIX + ".url";
	public static final String PROP_USERNAME = PROPERTY_KEY_PREFIX + ".username";
	public static final String PROP_PASSWORD = PROPERTY_KEY_PREFIX + ".password";

	public static final String PROP_CREATE_USERS = PROPERTY_KEY_PREFIX + ".create.users";

	// For the TemplateGroupIdAdapter
	public static final String PROP_REGEX = PROPERTY_KEY_PREFIX + ".groupname.regex";
	public static final String PROP_NAKID_TEMPLATE =  PROPERTY_KEY_PREFIX + ".groupid.template";

	// addIncludeExclude group suffixes
	public static final String PROP_SYSTEM_OF_RECORD_SUFFIX = "grouperIncludeExclude.systemOfRecord.extension.suffix";
	public static final String PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "grouperIncludeExclude.systemOfRecordAndIncludes.extension.suffix";
	public static final String PROP_INCLUDES_SUFFIX = "grouperIncludeExclude.include.extension.suffix";
	public static final String PROP_EXCLUDES_SUFFIX = "grouperIncludeExclude.exclude.extension.suffix";

	public static final String DEFAULT_SYSTEM_OF_RECORD_SUFFIX = "_systemOfRecord";
	public static final String DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "_systemOfRecordAndIncludes";
	public static final String DEFAULT_INCLUDES_SUFFIX = "_includes";
	public static final String DEFAULT_EXCLUDES_SUFFIX = "_excludes";

	public BaseGroupEsbConsumer() throws MalformedURLException {

		// Read and parse the settings.
		url = new URL(GrouperLoaderConfig.getPropertyString(PROP_URL, true));
		username = GrouperLoaderConfig.getPropertyString(PROP_USERNAME, true);
		password = GrouperLoaderConfig.getPropertyString(PROP_PASSWORD, true);

		includeExcludeSuffixes = new HashSet<String>();
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_INCLUDES_SUFFIX, DEFAULT_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_EXCLUDES_SUFFIX, DEFAULT_EXCLUDES_SUFFIX));

		templateGroupIdAdapter = new TemplateGroupIdAdapter();
		templateGroupIdAdapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		templateGroupIdAdapter.setPattern(Pattern.compile(GrouperLoaderConfig.getPropertyString(PROP_REGEX, true)));
		templateGroupIdAdapter.setNakamuraIdTemplate(GrouperLoaderConfig.getPropertyString(PROP_NAKID_TEMPLATE, true));
		templateGroupIdAdapter.setPseudoGroupSuffixes(NakamuraUtils.getPsuedoGroupSuffixes());
	}

	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> arg0,
			ChangeLogProcessorMetadata arg1) {
		// TODO Auto-generated method stub
		return -1;
	}

	/**
	 * Lazy-load the grouperSession
	 * @return
	 */
	protected GrouperSession getGrouperSession(){
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
}
