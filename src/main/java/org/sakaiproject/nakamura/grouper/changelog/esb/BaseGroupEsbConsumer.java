
package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;

public abstract class BaseGroupEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = LogFactory.getLog(BaseGroupEsbConsumer.class);

	protected URL url;
	protected String username;
	protected String password;

	protected boolean dryrun;
	protected boolean createUsers;
	protected boolean allowInstitutional;
	protected String deleteRole;

	// Suffixes for the composite groups created for addIncludeExclude groups
	protected Set<String> includeExcludeSuffixes;
	// Sakai OAE pseudoGroup suffixes.
	protected Set<String> pseudoGroupSuffixes;

	// Authenticated session for the Grouper API
	protected GrouperSession grouperSession;

	public static final String PROP_URL =      "url";
	public static final String PROP_USERNAME = "username";
	public static final String PROP_PASSWORD = "password";
	public static final String PROP_CREATE_USERS = "create.users";
	public static final String PROP_DRYRUN = "dryrun";
	public static final String PROP_ALLOW_INSTITUTIONAL = "allow.institutional";
	private static final String PROP_PSEUDOGROUP_SUFFIXES = "psuedoGroup.suffixes";

	public static final String ADD_INCLUDE_EXCLUDE = "addIncludeExclude";

	/**
	 * Load settings from grouper-loader.properties
	 * @param consumerName the name of this consumer job.
	 */
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
		allowInstitutional = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_ALLOW_INSTITUTIONAL, false);
		setPseudoGroupSuffixes(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PSEUDOGROUP_SUFFIXES, true));
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

	public void setPseudoGroupSuffixes(String psgConfig) {
		pseudoGroupSuffixes = new HashSet<String>();
		for(String suffix: StringUtils.split(psgConfig, ",")){
			pseudoGroupSuffixes.add(suffix.trim());
		}
	}
}
