
package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UnsupportedGroupException;

import com.google.common.collect.ImmutableSet;

import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;
import edu.internet2.middleware.grouper.util.GrouperUtil;

public abstract class BaseGroupEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = GrouperUtil.getLog(BaseGroupEsbConsumer.class);

	protected URL url;
	protected String username;
	protected String password;

	protected boolean dryrun;
	protected boolean createUsers;

	protected String createDeleteRole;

	// This job will try to process events for groups in these stems
	protected Set<String> supportedStems;
	protected String adhocStem;
	protected String provisionedStem;

	// Suffixes for the composite groups created for addIncludeExclude groups
	protected HashSet<String> includeExcludeSuffixes;
	// Sakai OAE pseudoGroup suffixes.
	protected Set<String> pseudoGroupSuffixes;

	// Authenticated session for the Grouper API
	protected GrouperSession grouperSession;

	public static final String PROP_URL =      "url";
	public static final String PROP_USERNAME = "username";
	public static final String PROP_PASSWORD = "password";
	public static final String PROP_CREATE_USERS = "create.users";
	public static final String PROP_DRYRUN = "dryrun";
	public static final String PROP_CREATE_DELETE_ROLE = "createDeleteRole";

	// Decides where we accept events from
	public static final String PROP_ADHOC_STEM = "adhoc.stem";
	public static final String PROP_PROVISIONED_STEM = "provisioned.stem";

	public static final String ADD_INCLUDE_EXCLUDE = "addIncludeExclude";
	// addIncludeExclude group suffixes
	public static final String PROP_SYSTEM_OF_RECORD_SUFFIX = "grouperIncludeExclude.systemOfRecord.extension.suffix";
	public static final String PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "grouperIncludeExclude.systemOfRecordAndIncludes.extension.suffix";
	public static final String PROP_INCLUDES_SUFFIX = "grouperIncludeExclude.include.extension.suffix";
	public static final String PROP_EXCLUDES_SUFFIX = "grouperIncludeExclude.exclude.extension.suffix";

	public static final String DEFAULT_SYSTEM_OF_RECORD_SUFFIX = "_systemOfRecord";
	public static final String DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX = "_systemOfRecordAndIncludes";
	public static final String DEFAULT_INCLUDES_SUFFIX = "_includes";
	public static final String DEFAULT_EXCLUDES_SUFFIX = "_excludes";

	public static final String PROP_SIMPLEGROUP_REGEX = "simplegroup.regex";
	public static final String PROP_COURSEGROUP_REGEX = "coursegroup.regex";

	protected void loadConfiguration(String consumerName){
		String cfgPrefix = "changeLog.consumer." + consumerName + ".";
		try {
			url = new URL(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_URL, true));
		} catch (MalformedURLException e) {
			String msg = "Unable to parse config. " +  cfgPrefix + PROP_URL;
			log.error(msg);
			throw new RuntimeException(msg);
		}
		username = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_USERNAME, true);
		password = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PASSWORD, true);
		dryrun = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_DRYRUN, false);
		createDeleteRole = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_CREATE_DELETE_ROLE, true);

		includeExcludeSuffixes = new HashSet<String>();
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX, DEFAULT_SYSTEM_OF_RECORD_AND_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_INCLUDES_SUFFIX, DEFAULT_INCLUDES_SUFFIX));
		includeExcludeSuffixes.add(GrouperLoaderConfig.getPropertyString(PROP_EXCLUDES_SUFFIX, DEFAULT_EXCLUDES_SUFFIX));

		pseudoGroupSuffixes = new HashSet<String>();
		String str = GrouperLoaderConfig.getPropertyString(cfgPrefix + "psuedoGroup.suffixes");
		for(String suffix: StringUtils.split(str, ",")){
			pseudoGroupSuffixes.add(suffix.trim());
		}
		adhocStem = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_ADHOC_STEM, true);
		provisionedStem = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PROVISIONED_STEM, true);

		supportedStems = ImmutableSet.of(adhocStem, provisionedStem);
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

	/**
	 * Does the group name fall inside of the stems we're configured to keep
	 * in sync with sakai?
	 * @param groupName
	 * @throws UnsupportedGroupException
	 */
	protected boolean isSupportedGroup(String groupName) {
		boolean supported = false;
		for (String stem: supportedStems){
			if (groupName.startsWith(stem)) {
				supported = true;
				break;
			}
		}
		return supported;
	}

	/**
	 * Is this a sub group of the addIncludeExclude group?
	 * @param grouperName
	 * @return
	 */
	protected boolean isIncludeExcludeSubGroup(String grouperName){
		for (String suffix: includeExcludeSuffixes){
			if (grouperName.endsWith(suffix)){
				return true;
			}
		}
		return false;
	}
}
