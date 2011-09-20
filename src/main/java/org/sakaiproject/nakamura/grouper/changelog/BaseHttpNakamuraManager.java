package org.sakaiproject.nakamura.grouper.changelog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;
import org.sakaiproject.nakamura.grouper.changelog.log.AuditLogUtils;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

import com.google.common.collect.MapMaker;

import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.subject.Subject;

/**
 * Shared functionality for the GroupAdapter classes goes in here.
 */
public abstract class BaseHttpNakamuraManager {

	private static Log log = LogFactory.getLog(BaseHttpNakamuraManager.class);

	// URI for the OAE user and group management servlets.
	public static final String USER_MANAGER_URI = "/system/userManager";
	public static final String GROUP_CREATE_URI = USER_MANAGER_URI + "/group.create.json";
	public static final String GROUP_PATH_PREFIX = USER_MANAGER_URI + "/group";
	public static final String USER_CREATE_URI = USER_MANAGER_URI + "/user.create.json";

	// Nakamura Batch servlet takes a JSONArray of JSONObjects that each represent a request
	public static final String BATCH_URI         = "/system/batch";
	public static final String BATCH_REQUESTS_PARAM = "requests";
	public static final String PARAMETERS_PARAM = "parameters";

	public static final String URL_PARAM = "url";
	public static final String OPERATION_PARAM = ":operation";
	public static final String METHOD_PARAM = "method";
	public static final String VIEWER_PARAM = ":viewer";
	public static final String MEMBER_PARAM = ":member";
	public static final String MANAGER_PARAM = ":manager";
	public static final String VIEWER_DELETE_PARAM = ":viewer@Delete";
	public static final String MEMBER_DELETE_PARAM = ":member@Delete";

	public static final String CHARSET_PARAM = "_charset_";
	public static final String UTF_8 = "utf-8";

	// Creates new files
	public static final String CREATE_FILE_URI   = "/system/pool/createfile";

	// Properties stored on authorizables in Sakai OAE
	public static final String GROUPER_NAME_PROP = "grouper:name";
	public static final String GROUPER_PROVISIONED_PROP = "grouper:provisioned";
	public static final String TRUE_VAL = "true";

	// Connection info for the OAE server
	public URL url;
	public String username;
	public String password;

	public boolean createUsers = false;

	// Avoid multiple user exists and user create HTTP calls
	protected Map<String,Boolean> userExistsInSakai;

	protected Map<String,Boolean> groupExistsInSakai;

	// Subject attributes for creating users
	public String firstNameAttribute;
	public String lastNameAttribute;
	public String emailAttribute;
	public String defaultEmailDomain;

	// Sakai OAE pseudoGroup suffixes (lecturer, ta, student...)
	public Set<String> pseudoGroupSuffixes;

	// Mock out calls to Sakai
	public boolean dryrun = false;

	// Convert grouper names to Sakai OAE groupIds
	public GroupIdAdapter groupIdAdapter;

	public BaseHttpNakamuraManager(){
		userExistsInSakai = new MapMaker().expireAfterWrite(30, TimeUnit.SECONDS).makeMap();
		groupExistsInSakai = new MapMaker().expireAfterWrite(30, TimeUnit.SECONDS).makeMap();
	}

	/**
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json :member=subjectId
	 */
	public void addMembership(String nakamuraGroupId, String memberId)
			throws GroupModificationException {
		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
		String role = StringUtils.substringAfterLast(nakamuraGroupId, "-");
		PostMethod method = new PostMethod(url.toString() + getUpdateURI(nakamuraGroupId));
        method.addParameter(MEMBER_PARAM, memberId);
        method.addParameter(VIEWER_PARAM, memberId);
        method.addParameter(CHARSET_PARAM, UTF_8);
        try {
        	if (!dryrun){
        		NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
        		AuditLogUtils.audit(AuditLogUtils.USER_ADDED, memberId, parentGroupId, role, AuditLogUtils.SUCCESS);
        	}
        	log.info("Added subjectId=" + memberId + " to group=" + nakamuraGroupId);
        }
        catch (GrouperException ge){
        	AuditLogUtils.audit(AuditLogUtils.USER_ADDED, memberId, parentGroupId, role, AuditLogUtils.FAILURE);
        	throw ge;
        }
	}

	/**
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json :member=subjectId
	 */
	public void addMemberships(String nakamuraGroupId, List<String> memberIds)
			throws GroupModificationException {
		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
		String role = StringUtils.substringAfterLast(nakamuraGroupId, "-");

		PostMethod method = new PostMethod(url.toString() + BATCH_URI);
		JSONArray requests = new JSONArray();
		for (String memberId : memberIds){
			JSONObject req = new JSONObject();
			req.put(METHOD_PARAM, "POST");
			req.put(CHARSET_PARAM, UTF_8);
			req.put(URL_PARAM, getUpdateURI(nakamuraGroupId));

			JSONObject params = new JSONObject();
			params.put(MEMBER_PARAM, memberId);
			params.put(VIEWER_PARAM, memberId);
			params.put(CHARSET_PARAM, UTF_8);

			req.put(PARAMETERS_PARAM, params);
			requests.add(req);
		}
		try {
			batchPost(requests);
			NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
			for (String memberId : memberIds){
				AuditLogUtils.audit(AuditLogUtils.USER_ADDED, memberId, parentGroupId, role, AuditLogUtils.SUCCESS);
			}
			log.info("Added subjectId=" + StringUtils.join(memberIds.toArray(), ",") + " to group=" + nakamuraGroupId);
		}
		catch (GrouperException ge){
			for (String memberId : memberIds){
				AuditLogUtils.audit(AuditLogUtils.USER_ADDED, memberId, parentGroupId, role, AuditLogUtils.FAILURE);
			}
			throw ge;
		}
	}

	/**
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json :member=subjectId
	 */
	public void deleteMembership(String nakamuraGroupId, String memberId)
			throws GroupModificationException {
		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
        String role = StringUtils.substringAfterLast(nakamuraGroupId, "-");
        PostMethod method = new PostMethod(url.toString() + getUpdateURI(nakamuraGroupId));
        method.addParameter(MEMBER_DELETE_PARAM, memberId);
        method.addParameter(VIEWER_DELETE_PARAM, memberId);
        method.addParameter(CHARSET_PARAM, UTF_8);
        try {
        	if (!dryrun){
        		NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
        		AuditLogUtils.audit(AuditLogUtils.USER_DELETED, memberId, parentGroupId, role, AuditLogUtils.SUCCESS);
        	}
        	log.info("Deleted subjectId=" + memberId + " from group=" + nakamuraGroupId );
        }
        catch (GrouperException ge){
        	AuditLogUtils.audit(AuditLogUtils.USER_DELETED, memberId, parentGroupId, role, AuditLogUtils.FAILURE);
        	throw ge;
        }
	}

	/**
	 * @return if this group exists in Sakai OAE.
	 */
	public boolean groupExists(String groupId){
		if (dryrun){
			return false;
		}

		if (!groupExistsInSakai.containsKey(groupId)){
			HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
			String groupUrl = url.toString() + GROUP_PATH_PREFIX + "/" + groupId + ".json";
			GetMethod method = new GetMethod(groupUrl);
			try {
				int responseCode = client.executeMethod(method);
				if (responseCode == HttpStatus.SC_OK){
					groupExistsInSakai.put(groupId, Boolean.TRUE);
				}
				log.debug(responseCode + " : " + groupUrl);
			}
			catch (Exception e){
				log.error(e.getMessage());
				throw new GrouperException(e.getMessage());
			}
		}
		return groupExistsInSakai.containsKey(groupId);
	}

	/**
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json key=value
	 */
	public void setProperty(String groupId, String key, String value) throws GroupModificationException {
		PostMethod method = new PostMethod(url.toString() + getUpdateURI(groupId));
		method.setParameter(key, value);
		method.setParameter(CHARSET_PARAM, UTF_8);
		try {
			if (!dryrun){
        		NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
                AuditLogUtils.audit(AuditLogUtils.GROUP_MODIFIED, null, groupId, key + "=" + value, AuditLogUtils.SUCCESS);
        	}
        	log.info("Set " + groupId + " : "+ key + "=" + value);
		}
		catch (GrouperException ge){
        	AuditLogUtils.audit(AuditLogUtils.GROUP_MODIFIED, null, groupId, key + "=" + value, AuditLogUtils.FAILURE);
        	throw ge;
        }
	}

	/**
	 * Create a user in OAE if it doesn't exist.
	 * @param userId
	 * @throws Exception
	 */
	public void createUser(String userId) throws UserModificationException {

		if (dryrun || userExists(userId)){
			return;
		}
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);

		String fullName = null;
		try {
			// throws exception if not found or not unique
			Subject subject = SubjectFinder.findByIdOrIdentifier(userId, true);

			String randomPassword = UUID.randomUUID().toString();
			PostMethod method = new PostMethod(url.toString() + USER_CREATE_URI);

			// Get properties from the Grouper Subject
			String firstName = subject.getAttributeValue(firstNameAttribute);
			if (firstName == null){
				firstName = "Firstname";
			}
			String lastName = subject.getAttributeValue(lastNameAttribute);
			if (lastName == null){
				lastName = "Lastname";
			}

			fullName = firstName + " " + lastName;

			String email = subject.getAttributeValue(emailAttribute);
			if (email == null){
				email = userId + "@" + defaultEmailDomain;
			}
			// Fill in the template
			String profileTemplate = "{\"basic\":{\"elements\":{\"firstName\":{\"value\":\"FIRSTNAME\"},\"lastName\":{\"value\":\"LASTNAME\"},\"email\":{\"value\":\"EMAIL\"}},\"access\":\"everybody\"},\"email\":\"EMAIL\"}}";
			profileTemplate = profileTemplate.replaceAll("FIRSTNAME", firstName)
								.replaceAll("LASTNAME", lastName)
								.replaceAll("EMAIL", email);

			method.addParameter(":name", userId);
			method.addParameter("pwd", randomPassword);
			method.addParameter("pwdConfirm", randomPassword);
			method.addParameter("firstName", firstName);
			method.addParameter("lastName", lastName);
			method.addParameter("email", email);
			method.addParameter("timezone", "America/New_York");
			method.addParameter("locale", "en_US");
			method.addParameter(":sakai:profile-import", profileTemplate);

			NakamuraHttpUtils.http(client, method);
			userExistsInSakai.put(userId, Boolean.TRUE);
			log.info("Created a user in Sakai OAE for " + userId);
			AuditLogUtils.audit(AuditLogUtils.USER_CREATED, userId, null, fullName, AuditLogUtils.SUCCESS);
		}
		catch (Exception e){
			log.error("Unable to create the user in Sakai OAE", e);
			AuditLogUtils.audit(AuditLogUtils.USER_CREATED, userId, null, fullName, AuditLogUtils.FAILURE);
			throw new UserModificationException(e.getMessage());
		}
	}

	/**
	 * Send a batch request to delete the group and its pseudoGroups
	 * @param groupId id of the group or one of its pseudoGroups
	 * @param groupName
	 * @throws GroupModificationException
	 */
	public void deleteGroup(String groupId, String groupName) throws GroupModificationException {
		String parentGroupId = groupIdAdapter.getPseudoGroupParent(groupId);
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);

		JSONArray batchRequests = new JSONArray();
		// Add the delete requests for the parent group
		JSONObject request = new JSONObject();
		request.put(METHOD_PARAM, "POST");
		request.put(CHARSET_PARAM, UTF_8);
		request.put(URL_PARAM, getDeleteURI(parentGroupId));
		JSONObject params = new JSONObject();
		params.put(OPERATION_PARAM, "delete");
		request.put(PARAMETERS_PARAM, params);
		batchRequests.add(request);

		// Add the delete requests for the pseudoGroups
		for (String suffix: pseudoGroupSuffixes){
			JSONObject psRequest = new JSONObject();
			psRequest.put(METHOD_PARAM, "POST");
			psRequest.put(CHARSET_PARAM, UTF_8);
			psRequest.put(URL_PARAM, getDeleteURI(parentGroupId + "-" + suffix));
			JSONObject psParams = new JSONObject();
			psParams.put(OPERATION_PARAM, "delete");
			psParams.put(PARAMETERS_PARAM, psParams);
			batchRequests.add(psRequest);
		}

		PostMethod method = new PostMethod(url + BATCH_URI);
		JSONArray json = JSONArray.fromObject(batchRequests);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		method.setParameter(CHARSET_PARAM, UTF_8);

		try {
			if (!dryrun){
				NakamuraHttpUtils.http(client, method);
				AuditLogUtils.audit(AuditLogUtils.GROUP_DELETED, null, parentGroupId, "deleted", AuditLogUtils.SUCCESS);
				for (String suffix: pseudoGroupSuffixes){
					AuditLogUtils.audit(AuditLogUtils.GROUP_DELETED, null, parentGroupId + "-" + suffix, "deleted", AuditLogUtils.SUCCESS);
				}
			}
		}
		catch (GroupModificationException e){
			AuditLogUtils.audit(AuditLogUtils.GROUP_DELETED, null, parentGroupId, "deleted", AuditLogUtils.FAILURE);
			for (String suffix: pseudoGroupSuffixes){
				AuditLogUtils.audit(AuditLogUtils.GROUP_DELETED, null, parentGroupId + "-" + suffix, "deleted", AuditLogUtils.FAILURE);
			}
			throw e;
		}
	}

	/**
	 * Create a pseudoGroup in Sakai OAE. 
	 * A pseudoGroup is an Authorizable that represents a role group for a course.
	 * @param nakamuraGroupId the id of the psuedoGroup in OAE
	 * @param groupName the name of the Grouper group.
	 * @param description a description for the group in OAE
	 * @throws GroupModificationException
	 */
	protected void createPseudoGroup(String nakamuraGroupId, String groupName, String description) throws GroupModificationException {
		String role = nakamuraGroupId.substring(nakamuraGroupId.lastIndexOf('-') + 1);
		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = new PostMethod(url + GROUP_CREATE_URI);
		method.addParameter(":name", nakamuraGroupId);
		method.addParameter(CHARSET_PARAM, UTF_8);
		method.addParameter("sakai:group-id", nakamuraGroupId);
		method.addParameter("sakai:excludeSearch", "true");
		method.addParameter("sakai:group-description", description);
		method.addParameter("sakai:group-title", nakamuraGroupId + "(" + role + ")");
		method.addParameter("sakai:pseudoGroup", "true");
		method.addParameter("sakai:pseudogroupparent", groupIdAdapter.getPseudoGroupParent(nakamuraGroupId));
		method.setParameter("sakai:group-joinable", "no");
		method.addParameter(GROUPER_NAME_PROP, groupName.substring(0, groupName.lastIndexOf(":") + 1 )
											+ nakamuraGroupId.substring(nakamuraGroupId.lastIndexOf("-") + 1));
		method.setParameter(GROUPER_PROVISIONED_PROP, TRUE_VAL);

		try {
			if (!dryrun){
				NakamuraHttpUtils.http(client, method);
			}
			log.info("Created pseudoGroup in OAE for " + nakamuraGroupId);
			AuditLogUtils.audit(AuditLogUtils.GROUP_CREATED, null, nakamuraGroupId, description, AuditLogUtils.SUCCESS);
		}
		catch (GroupModificationException e){
			AuditLogUtils.audit(AuditLogUtils.GROUP_CREATED, null, nakamuraGroupId, description, AuditLogUtils.FAILURE);
			throw e;
		}
	}

	/**
	 * Send a batch request to Sakai OAE.
	 * @param requests a JSONArray for JSONObjects. Each JSONObject represents a request.
	 * @throws GroupModificationException
	 */
	protected void batchPost(JSONArray requests) throws GroupModificationException{
		PostMethod method = new PostMethod(url + BATCH_URI);
		method.setParameter(BATCH_REQUESTS_PARAM, requests.toString());
		method.setParameter(CHARSET_PARAM, UTF_8);
		if (!dryrun){
			NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
		}
	}

	/**
	 * @param userId
	 * @return does this user exist in Sakai OAE?
	 */
	private boolean userExists(String userId){
		boolean exists = false;
		if (dryrun || userExistsInSakai.containsKey(userId)){
			exists = true;
		}
		if (!exists){
			try {
				HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
				int returnCode = client.executeMethod(new GetMethod(url.toString() + "/system/userManager/user/" + userId + ".json"));
				exists = (returnCode == HttpStatus.SC_OK);
			}
			catch (IOException ioe){
				log.error("Could not communicate with OAE to check if a user exists.");
			}
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
			url = new URL(urlString);
		}
		catch (MalformedURLException mfe){
			log.error("Could not parse " + urlString + "into a URL.");
			throw new RuntimeException(mfe.toString());
		}
	}
}
