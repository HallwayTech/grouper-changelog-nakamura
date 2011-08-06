package org.sakaiproject.nakamura.grouper.changelog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.exception.GrouperException;

/**
 * Shared functionality for the GroupAdapter classes goes in here.
 */
public abstract class BaseGroupAdapter {

	private static Log log = LogFactory.getLog(BaseGroupAdapter.class);

	// URI for the OAE user and group management servlets.
	protected static String USER_MANAGER_URI = "/system/userManager";
	protected static String GROUP_CREATE_URI = USER_MANAGER_URI + "/group.create.json";
	protected static String GROUP_PATH_PREFIX = USER_MANAGER_URI + "/group";
	protected static final String USER_CREATE_URI = USER_MANAGER_URI + "/user.create.json";

	// Nakamura Batch servlet takes a JSONArray of JSONObjects that each represent a request
	protected static final String BATCH_URI         = "/system/batch";
	protected static final String BATCH_REQUESTS_PARAM = "requests";

	protected static final String CHARSET_PARAM = "_charset_";
	protected static final String UTF_8 = "utf-8";

	// Creates new files
	protected static final String CREATE_FILE_URI   = "/system/pool/createfile";

	// Connection info for the OAE server
	protected URL url;
	protected String username;
	protected String password;

	protected boolean createUsers = false;

	protected Set<String> pseudoGroupSuffixes;

	protected boolean dryrun = false;

	protected GroupIdAdapter groupIdAdapter;

	/**
	 * Implemented for org.sakaiproject.grouper.changelog.api.NakamuraGroupAdapter
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json :member=subjectId
	 */
	public void addMembership(String nakamuraGroupId, String memberId)
			throws GroupModificationException {
        PostMethod method = new PostMethod(url.toString() + getUpdateURI(nakamuraGroupId));
        method.addParameter(":member", memberId);
        method.addParameter(":viewer", memberId);
        method.addParameter(CHARSET_PARAM, UTF_8);
        if (createUsers){
        	createOAEUser(memberId);
        }
        if (!dryrun){
            NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
        }
	    if (log.isInfoEnabled()){
	        log.info("Added subjectId=" + memberId + " to group=" + nakamuraGroupId);
	    }
	}

	/**
	 * Implemented for org.sakaiproject.grouper.changelog.api.NakamuraGroupAdapter
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json :member=subjectId
	 */
	public void deleteMembership(String nakamuraGroupId, String memberId)
			throws GroupModificationException {
        PostMethod method = new PostMethod(url.toString() + getUpdateURI(nakamuraGroupId));
        method.addParameter(":member@Delete", memberId);
        method.addParameter(":viewer@Delete", memberId);
        method.addParameter(CHARSET_PARAM, UTF_8);
        if (!dryrun){
            NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
        }
	    if (log.isInfoEnabled()){
	        log.info("Added deleted subjectId=" + memberId + " from group=" + nakamuraGroupId );
	    }
	}

	/**
	 * Implemented for org.sakaiproject.grouper.changelog.api.NakamuraGroupAdapter
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json key=value
	 */
	public void setProperty(String groupId, String key, String value) throws GroupModificationException {
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = new PostMethod(url.toString() + getUpdateURI(groupId));
		method.setParameter(key, value);
		method.setParameter(CHARSET_PARAM, UTF_8);
		if (!dryrun){
            NakamuraHttpUtils.http(client, method);
		}
		if (log.isInfoEnabled()){
			log.info("Set " + groupId + " : "+ key + "=" + value);
		}
	}

	protected void createPseudoGroup(String nakamuraGroupId, Group group) throws GroupModificationException {
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = new PostMethod(url + GROUP_CREATE_URI);
		method.addParameter(":name", nakamuraGroupId);
		method.addParameter(CHARSET_PARAM, UTF_8);
		method.addParameter("sakai:group-id", nakamuraGroupId);
		method.addParameter("sakai:excludeSearch", "true");
		method.addParameter("sakai:group-description", group.getDescription());
		method.addParameter("sakai:group-title", nakamuraGroupId + "(" + groupIdAdapter.getPseudoGroupParent(nakamuraGroupId) + ")");
		method.addParameter("sakai:pseudoGroup", "true");
		method.addParameter("sakai:pseudogroupparent", groupIdAdapter.getPseudoGroupParent(nakamuraGroupId));
		method.setParameter("sakai:group-joinable", "yes");
		method.addParameter("grouper:name", group.getParentStemName() + ":" + nakamuraGroupId.substring(nakamuraGroupId.lastIndexOf("-") + 1));
		if (!dryrun){
            NakamuraHttpUtils.http(client, method);
		}
		log.info("Created psuedoGroup in OAE for " + nakamuraGroupId);
	}

	/**
	 * Create a user in OAE if it doesn't exist.
	 * @param userId
	 * @throws Exception
	 */
	protected void createOAEUser(String userId) throws GroupModificationException {

		if (dryrun){
			return;
		}

		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		int returnCode = 0;
		try {
			returnCode = client.executeMethod(new GetMethod(url.toString() + "/system/userManager/user/" + userId + ".json"));
		}
		catch (IOException ioe){
			log.error("Could not communicate with OAE to create a user.");
			return;
		}

		if (returnCode == HttpStatus.SC_OK){
			log.info(userId + " already exists.");
			return;
		}

		if (returnCode == HttpStatus.SC_NOT_FOUND){
			String randomPassword = UUID.randomUUID().toString();
			PostMethod method = new PostMethod(url.toString() + USER_CREATE_URI);
			method.addParameter(":name", userId);
			method.addParameter("pwd", randomPassword);
			method.addParameter("pwdConfirm", randomPassword);
			if (!dryrun){
                NakamuraHttpUtils.http(client, method);
			}
			log.info("Created a user for " + userId);
		}
	}

	/**
	 * @return if this group exists in Sakai OAE.
	 */
	public boolean groupExists(String groupId){
		boolean exists = false;
		if (dryrun){
			return false;
		}
		else {
			HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
			GetMethod method = new GetMethod(url.toString() + GROUP_PATH_PREFIX + "/" + groupId + ".json");
			try {
				exists = (client.executeMethod(method) == HttpStatus.SC_OK);
			}
			catch (Exception e){
				log.error(e.getMessage());
				throw new GrouperException(e.getMessage());
			}
		}
		if (log.isDebugEnabled()){
			log.debug(groupId + " exists: " + exists);
		}
		return exists;
	}

	/**
	 * @param groupId the id of the OAE group
	 * @return the URI to the delete operation.
	 */
	protected String getDeleteURI(String groupId){
		return GROUP_PATH_PREFIX + "/" + groupId + ".delete.json";
	}

	/**
	 * @param groupId the id of the OAE group
	 * @return the URI to the update operation.
	 */
	protected String getUpdateURI(String groupId){
		return GROUP_PATH_PREFIX + "/" + groupId + ".update.json";
	}

	protected void setUrl(String urlString){
		try {
			setUrl(new URL(urlString));
		}
		catch (MalformedURLException mfe){
			log.error("Could not parse " + urlString + "into a URL.");
			throw new RuntimeException(mfe.toString());
		}
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setCreateUsers(boolean createUsers) {
		this.createUsers = createUsers;
	}

	public void setDryrun(boolean dryrun) {
		this.dryrun = dryrun;
	}

	public void setPseudoGroupSuffixes(Set<String> pseudoGroupSuffixes) {
		this.pseudoGroupSuffixes = pseudoGroupSuffixes;
	}

	public void setGroupIdAdapter(GroupIdAdapter gia){
		this.groupIdAdapter = gia;
	}
}
