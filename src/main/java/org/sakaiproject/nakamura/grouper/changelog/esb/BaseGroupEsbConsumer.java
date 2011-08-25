package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;

/**
 * Common data and methods for the other EsbConsumers.
 */
public abstract class BaseGroupEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = LogFactory.getLog(BaseGroupEsbConsumer.class);

	public static final String CONFIG_PREFIX = "changeLog.consumer";

	// See the README.md for details about the configuration.
	public static final String PROP_URL = "url";
	protected URL url;

	public static final String PROP_USERNAME = "username";
	protected String username;

	public static final String PROP_PASSWORD = "password";
	protected String password;

	public static final String PROP_DRYRUN = "dryrun";
	public static final boolean DEFAULT_DRYRUN = false;
	protected boolean dryrun = DEFAULT_DRYRUN;

	public static final String PROP_CREATE_USERS = "create.users";
	public static final boolean DEFAULT_CREATE_USERS = false;
	protected boolean createUsers = DEFAULT_CREATE_USERS;

	public static final String PROP_LDAP_ATTRIBUTE_FIRST_NAME = "firstname.attribute";
	protected String firstNameAttribute;

	public static final String PROP_LDAP_ATTRIBUTE_LAST_NAME = "lastname.attribute";
	protected String lastNameAttribute;

	public static final String PROP_LDAP_ATTRIBUTE_EMAIL = "email.attribute";
	protected String emailAttribute;

	public static final String PROP_ALLOW_INSTITUTIONAL = "allow.institutional";
	public static final boolean DEFAULT_ALLOW_INSTITUTIONAL = false;
	protected boolean allowInstitutional = DEFAULT_ALLOW_INSTITUTIONAL;

	public static final String PROP_PSEUDOGROUP_SUFFIXES = "pseudoGroup.suffixes";

	public static final String PROP_DELETE_ROLE = "delete.role";
	public static final String DEFAULT_DELETE_ROLE = "students";
	protected String deleteRole = DEFAULT_DELETE_ROLE;

	protected boolean configurationLoaded = false;

	// Suffixes for the composite groups created for addIncludeExclude groups
	protected Set<String> includeExcludeSuffixes;

	// Sakai OAE pseudoGroup suffixes.
	protected Set<String> pseudoGroupSuffixes;

	// Authenticated session for the Grouper API
	protected GrouperSession grouperSession;

	public static final String ADD_INCLUDE_EXCLUDE = "addIncludeExclude";

	/**
	 * Load settings from grouper-loader.properties
	 * @param consumerName the name of this consumer job.
	 */
	protected void loadConfiguration(String consumerName){

		if (configurationLoaded){
			return;
		}

		String cfgPrefix = CONFIG_PREFIX + consumerName + ".";
		try {
			url = new URL(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_URL, true));
		} catch (MalformedURLException e) {
			String msg = "Unable to parse config. " +  cfgPrefix + PROP_URL;
			log.error(msg);
			throw new RuntimeException(msg);
		}
		username = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_USERNAME, true);
		log.debug("Sakai OAE username " + username);
		password = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PASSWORD, true);
		log.debug("Sakai OAE password " + password);
		dryrun = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_DRYRUN, DEFAULT_DRYRUN);
		deleteRole = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_DELETE_ROLE, DEFAULT_DELETE_ROLE);

		allowInstitutional = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_ALLOW_INSTITUTIONAL, DEFAULT_ALLOW_INSTITUTIONAL);
		createUsers = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_CREATE_USERS, DEFAULT_CREATE_USERS);
		firstNameAttribute = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_LDAP_ATTRIBUTE_FIRST_NAME, false);
		lastNameAttribute = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_LDAP_ATTRIBUTE_LAST_NAME, false);
		emailAttribute = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_LDAP_ATTRIBUTE_EMAIL, false);

		setPseudoGroupSuffixes(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PSEUDOGROUP_SUFFIXES, true));
		configurationLoaded = true;
	}

	/**
	 * Lazy-load the grouperSession
	 * @return
	 */
	protected GrouperSession getGrouperSession(){
		if ( grouperSession == null) {
			try {
				grouperSession = GrouperSession.startRootSession();
				log.debug("started session: " + this.grouperSession);
			}
			catch (SessionException se) {
				throw new GrouperException("Error starting session: " + se.getMessage(), se);
			}
		}
		return grouperSession;
	}

	public void setPseudoGroupSuffixes(String psgConfig) {
		pseudoGroupSuffixes = new HashSet<String>();
		for(String suffix: StringUtils.split(psgConfig, ",")){
			pseudoGroupSuffixes.add(suffix.trim());
		}
	}

	public void setAllowInstitutional(boolean allow){
		this.allowInstitutional = allow;
	}

	public void setDeleteRole(String role){
		this.deleteRole = role;
	}

	public void setConfigurationLoaded(boolean loaded){
		this.configurationLoaded = loaded;
	}
}
