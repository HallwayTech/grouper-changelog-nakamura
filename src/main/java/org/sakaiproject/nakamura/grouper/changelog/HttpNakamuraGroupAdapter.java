package org.sakaiproject.nakamura.grouper.changelog;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraGroupAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;
import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.util.api.InitialGroupPropertiesProvider;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.exception.GrouperException;

/**
 * Responds to Grouper changelog events by HTTP POSTing to the nakamura group servlets.
 * @see edu.internet2.middleware.grouper.changelog.*
 */
public class HttpNakamuraGroupAdapter implements NakamuraGroupAdapter {
	
	private Log log = LogFactory.getLog(HttpNakamuraGroupAdapter.class);
	
	private static String GROUP_CREATE_PATH = "/system/userManager/group.create.json";
	private static String GROUP_PATH_PREFIX = "/system/userManager/group/";

	// Nakamura URL
	private URL url;
	private String username;
	private String password;
	
	// Sets HTTP POST params that are stored in nakamura as properties on the group. 
	private InitialGroupPropertiesProvider initialPropertiesProvider;
	
	// Maps grouper gouprName -> nakamura groupId
	private GroupIdAdapter groupIdAdapter;

	/**
	 * POST to http://localhost:8080/system/userManager/group.create.json
	 * @see org.sakaiproject.nakamura.user.servlet.CreateSakaiGroupServlet
	 */
	public void createGroup(Group group) throws GroupModificationException {

		String nakamuraGroupName = groupIdAdapter.getNakamuraGroupId(group.getName());
		if(log.isDebugEnabled()){
			log.debug(group.getName() + " converted to " + nakamuraGroupName + " for nakamura.");
		}

		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = new PostMethod(url.toString() + GROUP_CREATE_PATH);
	    method.addParameter(":name", nakamuraGroupName);
	    initialPropertiesProvider.addProperties(group, nakamuraGroupName,  method);

	    String errorMessage = null;
	    String responseString = null;
	    JSONObject responseJSON = null;

	    try{
	    	int returnCode = client.executeMethod(method);
	    	responseString = IOUtils.toString(method.getResponseBodyAsStream());
	    	responseJSON = JSONObject.fromObject(responseString);

	    	switch (returnCode){
	    	// 200
			case HttpStatus.SC_OK:
				if (log.isInfoEnabled()){
					log.info("SUCCESS: created a group for " + group.getName());
				}
				break;
			// 400
			case HttpStatus.SC_BAD_REQUEST:
				String statusMessage = responseJSON.getString("status.message"); 
				if (statusMessage.startsWith("A principal already exists")){
					if (log.isInfoEnabled()){
						log.info("Create event for a group that already exists: " + group.getName());
					}
				}
				else {
					errorMessage = "FAILURE: 400 : Unable to create a group for " + group.getName() + ". " + statusMessage;
				}
				break;
			// 403
			case HttpStatus.SC_FORBIDDEN: 
				errorMessage = "FAILURE: 403: Unable to create a group for " + group.getName()
						+ ". Check the username and password.";
				break;
			// 500
			case HttpStatus.SC_INTERNAL_SERVER_ERROR:
				errorMessage = "FAILURE: 500: Unable to create a group for " + group.getName() + " response: " + responseString;
				break;
			// ?
			default:
				errorMessage = "FAILURE: " + returnCode + ": Unable to create a group for " + group.getName();
				logUnhandledResponse(returnCode, responseString);
				break;
	    	}
	    }
	    catch (Exception e) {
	    	errorMessage = "An exception occurred while creating the group. " + e.toString();
	    } 
	    finally {
	    	method.releaseConnection();
	    }

	    if (errorMessage != null){
	    	if (log.isErrorEnabled()){ 
	    		log.error(errorMessage);
	    	}
	    	throw new GroupModificationException(errorMessage);
	    }
	}

	/**
	 * Delete a group from sakai3
	 * curl -Fgo=1 http://localhost:8080/system/userManager/group/groupId.delete.json
	 */
	public void deleteGroup(String groupId, String groupName) throws GroupModificationException {

		String nakamuraGroupName = groupIdAdapter.getNakamuraGroupId(groupName);

		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
	    PostMethod method = new PostMethod(url.toString() + getDeletePath(nakamuraGroupName));
	    method.addParameter("go", "1");
	    String errorMessage = null;
	    String response = null;

	    try{
	    	int returnCode = client.executeMethod(method);
	    	response = IOUtils.toString(method.getResponseBodyAsStream());

	    	switch (returnCode){
			case HttpStatus.SC_OK:
			case HttpStatus.SC_CREATED:
				if (log.isInfoEnabled()){
	    			log.info("SUCCESS: deleted group " + nakamuraGroupName);
				}
	    		break;
			case HttpStatus.SC_NOT_FOUND:
				if (log.isInfoEnabled()){
	    			log.info("SUCCESS: group " + nakamuraGroupName + " did not exist.");
				}
	    		break;
			case HttpStatus.SC_INTERNAL_SERVER_ERROR:
				errorMessage = "FAILURE: 500: Unable to delete group " + nakamuraGroupName;
				break;
			case HttpStatus.SC_FORBIDDEN:
				errorMessage = "FAILURE: 403: Unable to create a group for " + nakamuraGroupName
						+ ". Check the username and password.";
				break;
			default:
				errorMessage = "FAILURE: " + returnCode + ": Unable to delete group " + nakamuraGroupName;
				logUnhandledResponse(returnCode, response);
				break;
	    	}
	    } catch (Exception e) {
	    	errorMessage = "An exception occurred while deleting the group. " + groupId
	    		  			+ " Error: " + e.toString();
	    } finally {
	      method.releaseConnection();
	    }

	    if (errorMessage != null){
	    	if (log.isErrorEnabled()){ 
	    		log.error(errorMessage);
	    	}
	    	throw new GroupModificationException(errorMessage);
	    }
	}

	/**
	 * Add a subjectId to a group by POSTing to:
	 * http://localhost:8080/system/userManager/group/groupId.update.html :member=subjectId
	 */
	public void addMembership(String groupId, String groupName, String subjectId)
			throws GroupModificationException {
		String nakamuraGroupName = groupIdAdapter.getNakamuraGroupId(groupName);
		PostMethod method = new PostMethod(url.toString() + getUpdatePath(nakamuraGroupName));
	    method.addParameter(":member", subjectId);
	    updateGroupMembership(groupId, subjectId, method);
	    if (log.isInfoEnabled()){
	    	log.info("SUCCESS: add subjectId=" + subjectId + " to group=" + nakamuraGroupName );
	    }
	}

	/**
	 * Delete a subjectId from a group by POSTing to:
	 * http://localhost:8080/system/userManager/group/groupId.update.html :member=subjectId
	 */
	public void deleteMembership(String groupId, String groupName, String subjectId)
			throws GroupModificationException {
		String nakamuraGroupName = groupIdAdapter.getNakamuraGroupId(groupName);
		PostMethod method = new PostMethod(url.toString() + getUpdatePath(nakamuraGroupName));
	    method.addParameter(":member@Delete", subjectId);
	    updateGroupMembership(nakamuraGroupName, subjectId, method);
	    if (log.isInfoEnabled()){
	    	log.info("SUCCESS: deleted subjectId=" + subjectId + " from group=" + nakamuraGroupName );
	    }
	}

	/**
	 * Add or delete a subject group membership.
	 * @param groupName the id of the group being modified.
	 * @param subjectId the id of the subject being added or remove.
	 * @param method the POST method to send to nakamura
	 * @return
	 */
	private void updateGroupMembership(String groupName, String subjectId, PostMethod method) throws GroupModificationException {
	    String errorMessage = null;
	    String response = null;

	    try{
	    	HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
	    	checkUserExists(subjectId);
	    	int returnCode = client.executeMethod(method);
	    	response = IOUtils.toString(method.getResponseBodyAsStream());

	    	switch (returnCode){
			case HttpStatus.SC_OK:
				break;
			case HttpStatus.SC_CREATED:
			case HttpStatus.SC_INTERNAL_SERVER_ERROR:
			case HttpStatus.SC_NOT_FOUND:
				errorMessage = "FAILURE: " + returnCode + " - group " + groupName;
				break;
			case HttpStatus.SC_FORBIDDEN:
				errorMessage = "FAILURE: 403 - " + groupName + " - Check the username and password.";
				break;
			default:
				errorMessage = "FAILURE: Unable to modify subject membership: subject=" + subjectId 
						+ " group=" + groupName;
				logUnhandledResponse(returnCode, response);
				break;
	    	} //end switch
	    } catch (Exception e) {
	    	errorMessage = "An exception occurred while modifying group membership. subjectId=" + subjectId + 
	    		  	" group=" + groupName + " Error: " + e.toString();
	    } finally {
	      method.releaseConnection();
	    }

	    if (errorMessage != null){
	    	if (log.isErrorEnabled()){
	    		log.error(errorMessage);
	    	}
	    	throw new GroupModificationException(errorMessage);
	    }
	}

	public boolean groupExists(String groupId){
		boolean exists = false;
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		GetMethod method = new GetMethod(url.toString() + GROUP_PATH_PREFIX + groupId + ".json");

		try {
			int returnCode = client.executeMethod(method);
			if (returnCode == HttpStatus.SC_OK){
				exists = true;
			}
		}
		catch (Exception e){
			log.error(e.getMessage());
			throw new GrouperException(e.getMessage());
		}
		return exists;
	}

	/**************************************************************************/

	private String getUpdatePath(String groupId){
		return GROUP_PATH_PREFIX + groupId + ".update.json";
	}

	private String getDeletePath(String groupId){
		return GROUP_PATH_PREFIX + groupId + ".delete.json";
	}

	private void checkUserExists(String userId) throws Exception {
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		GetMethod method = new GetMethod(url.toString() + "/system/userManager/user/" + userId + ".json");
		int returnCode = client.executeMethod(method); 

		if (returnCode == HttpStatus.SC_OK){
			return;
		}
		if (returnCode == HttpStatus.SC_NOT_FOUND){
			PostMethod post = new PostMethod(url.toString() + "/system/userManager/user.create.json");
			post.addParameter(":name", userId);
			String randomPassword = UUID.randomUUID().toString();
			post.addParameter("pwd", randomPassword);
			post.addParameter("pwdConfirm", randomPassword);
			int userCreateResponseCode = client.executeMethod(post);
			if (userCreateResponseCode == HttpStatus.SC_OK || userCreateResponseCode == HttpStatus.SC_CREATED){
				log.info("Created a user for " + userId);
			}
			else {
				throw new Exception("HTTP error: " + userCreateResponseCode + " while creating user for: " + userId);
			}
		}
	}

	public void logUnhandledResponse(int responseCode, String response){
		if (log.isErrorEnabled()){
			log.error("Unhandled response. code=" + responseCode + "\nResponse: " + response);
		}
	}

	public void setInitialPropertiesProvider(
			InitialGroupPropertiesProvider initialPropertiesProvider) {
		this.initialPropertiesProvider = initialPropertiesProvider;
	}

	public void setGroupIdAdapter(GroupIdAdapter groupIdAdapter) {
		this.groupIdAdapter = groupIdAdapter;
	}

	public void setUrl(String urlString){
		try {
			setUrl(new URL(urlString));
		}
		catch (MalformedURLException mfe){
			log.error("Could not parse " + urlString + "into a String");
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
}