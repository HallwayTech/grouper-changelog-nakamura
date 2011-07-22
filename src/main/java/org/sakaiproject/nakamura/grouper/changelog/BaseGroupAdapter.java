package org.sakaiproject.nakamura.grouper.changelog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.mortbay.jetty.HttpHeaders;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupAlreadyExistsException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.util.GrouperUtil;

public class BaseGroupAdapter {

	private static Log log = GrouperUtil.getLog(BaseGroupAdapter.class);

	private static final String HTTP_REFERER = "/system/console/grouper";
	private static final String HTTP_USER_AGENT = "Nakamura Grouper Sync";

	// URI for the OAE user and group management servlets.
	protected static String USER_MANAGER_URI = "/system/userManager";
	protected static String GROUP_CREATE_URI = USER_MANAGER_URI + "/group.create.json";
	protected static String GROUP_PATH_PREFIX = USER_MANAGER_URI + "/group";
	protected static final String USER_CREATE_URI = USER_MANAGER_URI + "/user.create.json";

	protected static final String BATCH_URI         = "/system/batch";
	protected static final String CREATE_FILE_URI   = "/system/pool/createfile";

	protected static final String BATCH_REQUESTS_PARAM = "requests";

	protected URL url;
	protected String username;
	protected String password;

	protected boolean createUsers = false;

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

	/**
	 * Add a subjectId to a group by POSTing to:
	 * http://localhost:8080/system/userManager/group/groupId.update.html :member=subjectId
	 */
	public void addMembership(String nakamuraGroupId, String memberId)
			throws GroupModificationException {
        PostMethod method = new PostMethod(url.toString() + getUpdateURI(nakamuraGroupId));
        method.addParameter(":member", memberId);
        method.addParameter(":viewer", memberId);
        method.addParameter("_charset_", "utf-8");
        if (createUsers){
        	createOAEUser(memberId);
        }
        http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
	    if (log.isInfoEnabled()){
	        log.info("SUCCESS: add subjectId=" + memberId + " to group=" + nakamuraGroupId);
	    }
	}

	/**
	 * Delete a subjectId from a group by POSTing to:
	 * http://localhost:8080/system/userManager/group/groupId.update.html :member=subjectId
	 */
	public void deleteMembership(String nakamuraGroupId, String memberId)
			throws GroupModificationException {
        PostMethod method = new PostMethod(url.toString() + getUpdateURI(nakamuraGroupId));
        method.addParameter(":member@Delete", memberId);
        method.addParameter(":viewer@Delete", memberId);
        method.addParameter("_charset_", "utf-8");
        http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
	    if (log.isInfoEnabled()){
	        log.info("SUCCESS: deleted subjectId=" + memberId + " from group=" + nakamuraGroupId );
	    }
	}

	protected void createPseudoGroup(String nakamuraGroupId, Group group) throws GroupModificationException {
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = new PostMethod(url + GROUP_CREATE_URI);
		method.addParameter(":name", nakamuraGroupId);
		method.addParameter("_charset_", "utf-8");
		method.addParameter("sakai:group-id", nakamuraGroupId);
		method.addParameter("sakai:excludeSearch", "true");
		method.addParameter("sakai:group-description", group.getDescription());
		method.addParameter("sakai:group-title", nakamuraGroupId + "(" + getPseudoGroupParent(nakamuraGroupId) + ")");
		method.addParameter("sakai:pseudoGroup", "true");
		method.addParameter("sakai:pseudogroupparent", getPseudoGroupParent(nakamuraGroupId));
		method.setParameter("sakai:joinable", "yes");
		method.addParameter("grouper:name", group.getParentStemName() + ":" + nakamuraGroupId.substring(nakamuraGroupId.lastIndexOf("-") + 1));
		http(client, method);
	}

	/**
	 * Create a user in OAE if it doesn't exist.
	 * @param userId
	 * @throws Exception
	 */
	protected void createOAEUser(String userId) throws GroupModificationException {
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
			return;
		}

		if (returnCode == HttpStatus.SC_NOT_FOUND){
			String randomPassword = UUID.randomUUID().toString();
			PostMethod method = new PostMethod(url.toString() + USER_CREATE_URI);
			method.addParameter(":name", userId);
			method.addParameter("pwd", randomPassword);
			method.addParameter("pwdConfirm", randomPassword);
			http(client, method);
			log.info("Created a user for " + userId);
		}
	}

	/**
	 * Return the authorizableId of the parent group for this group.
	 * @param nakamuraGroupId
	 * @return
	 */
	protected String getPseudoGroupParent(String nakamuraGroupId){
		int index = nakamuraGroupId.lastIndexOf("-");
		if (index == -1){
			return nakamuraGroupId;
		}
		else {
			return nakamuraGroupId.substring(0, index);
		}
	}

	/**
	 * @return if this group exists in Sakai OAE.
	 */
	public boolean groupExists(String groupId){
		boolean exists = false;
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		GetMethod method = new GetMethod(url.toString() + GROUP_PATH_PREFIX + "/" + groupId + ".json");
		try {
			exists = (client.executeMethod(method) == HttpStatus.SC_OK);
		}
		catch (Exception e){
			log.error(e.getMessage());
			throw new GrouperException(e.getMessage());
		}
		return exists;
	}

	/**
	 * Prepare an HTTP request to Sakai OAE and parse the response (if JSON).
	 * @param client an {@link HttpClient} to execute the request.
	 * @param method an HTTP method to send
	 * @return a JSONObject of the response if the request returns JSON
	 * @throws GroupModificationException if there was an error updating the group information.
	 */
	public JSONObject http(HttpClient client, PostMethod method) throws GroupModificationException {

		method.setRequestHeader(HttpHeaders.USER_AGENT, HTTP_USER_AGENT);
		method.setRequestHeader(HttpHeaders.REFERER, HTTP_REFERER);

		String errorMessage = null;
		String responseString = null;
		JSONObject responseJSON = null;

		try{
			int responseCode = client.executeMethod(method);
			responseString = StringUtils.trimToNull(IOUtils.toString(method.getResponseBodyAsStream()));

			boolean isJSONRequest = ! method.getURI().toString().endsWith(".html");
			if (responseString != null && isJSONRequest){
				try {
					responseJSON = JSONObject.fromObject(responseString);
				}
				catch (JSONException je){
					if (responseString.startsWith("<html>")){
						log.error("Excepted a JSON response, got html at " + method.getURI());
					}
					else {
						log.error("Could not parse JSON response. " + responseString);
					}
				}
			}

			switch (responseCode){

			case HttpStatus.SC_OK: // 200
			case HttpStatus.SC_CREATED: // 201
				break;
			case HttpStatus.SC_BAD_REQUEST: // 400
			case HttpStatus.SC_UNAUTHORIZED: // 401
			case HttpStatus.SC_FORBIDDEN: // 404
			case HttpStatus.SC_INTERNAL_SERVER_ERROR: // 500
				if (isJSONRequest && responseJSON != null){
					errorMessage = responseJSON.getString("status.message");
				}
				if (errorMessage == null){
					errorMessage = "Empty "+ responseCode + " error. Check the logs on the Sakai OAE server at " + url;
				}
				break;
			default:
				if (log.isErrorEnabled()){
					log.error("Unhandled response. code=" + responseCode + "\nResponse: " + responseString);
				}
				errorMessage = "Unknown HTTP response " + responseCode;
				break;
			}
		}
		catch (Exception e) {
			errorMessage = "An exception occurred communicatingSakai OAE. " + e.toString();
		}
		finally {
			method.releaseConnection();
		}

		if (errorMessage != null){
			log.error(errorMessage);
			errorToException(errorMessage);
		}
		return responseJSON;
	}

	/**
	 * Throw a specific exception given a JSON response from a Sakai OAE server.
	 * @param response
	 * @throws GroupModificationException
	 * @throws GroupAlreadyExistsException
	 */
	protected void errorToException(String errorMessage) throws GroupModificationException, GroupAlreadyExistsException {
		if (errorMessage == null){
			return;
		}
		// TODO: If this is a constant somewhere include the nakamura jar in the
		// lib directory and use the constant.
		if (errorMessage.startsWith("A principal already exists with the requested name")){
			throw new GroupAlreadyExistsException(errorMessage);
		}

		throw new GroupModificationException(errorMessage);
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

	protected void setUrl(URL url) {
		this.url = url;
	}

	protected void setUsername(String username) {
		this.username = username;
	}

	protected void setPassword(String password) {
		this.password = password;
	}

	public void setCreateUsers(boolean createUsers) {
		this.createUsers = createUsers;
	}
}
