
package org.sakaiproject.nakamura.grouper.changelog.esb;

import org.apache.commons.logging.Log;

import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;
import edu.internet2.middleware.grouper.util.GrouperUtil;

public abstract class BaseGroupEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = GrouperUtil.getLog(BaseGroupEsbConsumer.class);


	// Authenticated session for the Grouper API
	protected GrouperSession grouperSession;

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
