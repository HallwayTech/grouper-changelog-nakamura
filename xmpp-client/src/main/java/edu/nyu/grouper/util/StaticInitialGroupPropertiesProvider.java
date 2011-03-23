package edu.nyu.grouper.util;

import org.apache.commons.httpclient.methods.PostMethod;

import edu.internet2.middleware.grouper.Group;
import edu.nyu.grouper.xmpp.api.InitialGroupPropertiesProvider;

/**
 * Provide a set of static properties for new sakai groups.
 * @author froese
 */
public class StaticInitialGroupPropertiesProvider implements InitialGroupPropertiesProvider {

	public void addProperties(Group group, PostMethod method) {
		
		// Basic info
		method.addParameter("sakai:group-id", group.getId());
		method.addParameter("sakai:group-title", group.getDisplayName());
		method.addParameter("sakai:group-description", group.getDescription());
		method.addParameter("sakai:pages-template", "/var/templates/site/defaultgroup");
		
		// Authorizations
		method.addParameter("group-joinable", "no");
		method.addParameter("group-visible", "members-only");
		method.addParameter("sakai:pages-visible", "members-only");
		
		// Grouper
		method.addParameter("grouper:extension", group.getExtension());
		method.addParameter("grouper:uuid", group.getUuid());
	}
}
