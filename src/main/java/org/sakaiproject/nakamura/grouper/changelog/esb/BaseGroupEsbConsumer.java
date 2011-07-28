
package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.commons.logging.Log;

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
	protected boolean dryrun;

	// Authenticated session for the Grouper API
	protected GrouperSession grouperSession;

	// Configuration values from conf/grouper-loader.properties
	public static final String PROP_URL =      PROPERTY_KEY_PREFIX + ".url";
	public static final String PROP_USERNAME = PROPERTY_KEY_PREFIX + ".username";
	public static final String PROP_PASSWORD = PROPERTY_KEY_PREFIX + ".password";

	public static final String PROP_DRYRUN = PROPERTY_KEY_PREFIX + ".dryrun";

	public static final String PROP_CREATE_USERS = PROPERTY_KEY_PREFIX + ".create.users";

	public BaseGroupEsbConsumer() throws MalformedURLException {
		loadConfiguration();
	}

	protected void loadConfiguration() throws MalformedURLException {
		url = new URL(GrouperLoaderConfig.getPropertyString(PROP_URL, true));
		username = GrouperLoaderConfig.getPropertyString(PROP_USERNAME, true);
		password = GrouperLoaderConfig.getPropertyString(PROP_PASSWORD, true);
		dryrun = GrouperLoaderConfig.getPropertyBoolean(PROP_DRYRUN, false);
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
