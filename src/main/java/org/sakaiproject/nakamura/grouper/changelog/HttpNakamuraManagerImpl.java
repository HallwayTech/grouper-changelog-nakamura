package org.sakaiproject.nakamura.grouper.changelog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
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
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdManager;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;
import org.sakaiproject.nakamura.grouper.changelog.api.WorldConstants;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;
import org.sakaiproject.nakamura.grouper.changelog.log.AuditLogUtils;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;

import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.subject.Subject;

/**
 * Shared functionality for the GroupAdapter classes goes in here.
 */
public class HttpNakamuraManagerImpl implements NakamuraManager {

	private static Log log = LogFactory.getLog(HttpNakamuraManagerImpl.class);

	// URI for the OAE user and group management servlets.
	public static final String USER_MANAGER_URI = "/system/userManager";
	public static final String GROUP_CREATE_URI = USER_MANAGER_URI + "/group.create.json";
	public static final String GROUP_PATH_PREFIX = USER_MANAGER_URI + "/group";
	public static final String WORLD_CREATE_URI = "/system/world/create";
	public static final String USER_CREATE_URI = USER_MANAGER_URI + "/user.create.json";

	// Nakamura Batch servlet takes a JSONArray of JSONObjects that each represent a request
	public static final String BATCH_URI         = "/system/batch";
	public static final String BATCH_REQUESTS_PARAM = "requests";
	public static final String PARAMETERS_PARAM = "parameters";

	// Creating worlds
	public static final String DATA_PARAM = "data";
	public static final String URL_PARAM = "url";
	public static final String ID_PARAM = "id";
	public static final String TITLE_PARAM = "title";
	public static final String TAGS_PARAM = "tags";
	public static final String DESCRIPTION_PARAM = "description";
	public static final String VISIBILITY_PARAM = "visibility";
	public static final String JOINABILITY_PARAM = "joinability";
	public static final String WORLD_TEMPLATE_PARAM = "worldTemplate";
	public static final String MESSAGE_PARAM = "message";
	public static final String USERS_TO_ADD_PARAM = "usersToAdd";

	public static final String COURSE_DEFAULT_ADMIN_ROLE = "lecturer";
	public static final String SIMPLE_GROUP_DEFAULT_ADMIN_ROLE = "lecturer";

	// Sling params
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
	public GroupIdManager groupIdAdapter;

	public HttpNakamuraManagerImpl(){
		userExistsInSakai = new MapMaker().expireAfterWrite(30, TimeUnit.SECONDS).makeMap();
		groupExistsInSakai = new MapMaker().expireAfterWrite(30, TimeUnit.SECONDS).makeMap();
	}

	/**
	 * Create the full set of objects that are necessary to have a working
	 * course in Sakai OAE.
	 *
	 * Written on the back of the amazing John King who pulled out all of this
	 * for nakamura/testscripts/SlingRuby/dataload/full_group_creator.rb
	 * @throws GroupModificationException
	 */
	@Override
	public void createWorld(String grouperName, String worldId, String title,
			String description, String[] tags, String visibility,
			String joinability, String template, String message,
			Map<String, String> usersRolesToAdd)
			throws GroupModificationException {

		JSONObject params = makeCreateWorldParams(worldId, worldId, tags, worldId,
				visibility, joinability, template, message, usersRolesToAdd);

		PostMethod method = new PostMethod(url + WORLD_CREATE_URI);
		method.setParameter(DATA_PARAM, params.toString());
		method.setParameter(CHARSET_PARAM, UTF_8);

		if(!dryrun){
			HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
			NakamuraHttpUtils.http(client, method);
			setGrouperNameProperties(worldId, grouperName);
		}

	    log.info("Successfully created the OAE world " + worldId + " for " + grouperName);
	}

	/**
	 * Set the grouper:* properties on the group authorizables in OAE
	 * @param worldId
	 * @param grouperName
	 * @throws GroupModificationException
	 */
	private void setGrouperNameProperties(String worldId, String grouperName) throws GroupModificationException{
		String parentId = groupIdAdapter.getPseudoGroupParent(worldId);
		List<String> roles = getRoles(parentId);
		for (String role : roles){
			setProperties(worldId + "-" + role,
				ImmutableMap.of(GROUPER_NAME_PROP, StringUtils.substringBeforeLast(grouperName, ":") + role,
								GROUPER_PROVISIONED_PROP, "1"));
		}
	}

	/**
	 * For a description of the params see
	 * {@link NakamuraManager#createWorld(String, String, String, String, String[], String, String, String, String, Map)}
	 * @return the JSONObject the represents the new World request.
	 */
	private JSONObject makeCreateWorldParams(String id, String title, String[] tags, String description,
			String visibility, String joinability, String template, String message, Map<String, String> usersRolesToAdd){
		JSONObject params = new JSONObject();
		params.put(ID_PARAM, id);
		params.put(TITLE_PARAM, title);
		params.put(TAGS_PARAM, "[\"" + StringUtils.join(tags, ",") + "\"]");
		params.put(DESCRIPTION_PARAM, description);
		params.put(VISIBILITY_PARAM, visibility);
		params.put(JOINABILITY_PARAM, joinability);
		params.put(WORLD_TEMPLATE_PARAM, template);
		params.put(MESSAGE_PARAM, message);
		JSONArray toAdd = new JSONArray();
		for (Entry<String, String> entry : usersRolesToAdd.entrySet()){
			JSONObject jsonEntry = new JSONObject();
			jsonEntry.put("userid", entry.getKey());
			jsonEntry.put("role", entry.getValue());
			toAdd.add(jsonEntry);
		}
		params.put(USERS_TO_ADD_PARAM, toAdd.toString());
		return params;
	}

	/*
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

	public void addMemberships(String nakamuraGroupId, List<String> memberIds)
			throws GroupModificationException {

		if (memberIds.isEmpty()){
			return;
		}
		else if (memberIds.size() == 1){
			addMembership(nakamuraGroupId, memberIds.get(0));
			return;
		}

		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
		String role = StringUtils.substringAfterLast(nakamuraGroupId, "-");

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
			// Use the OAE batch service to add multiple memberships in one HTTP POST
			batchPost(requests);
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

	/*
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json?
	 * :member@Delete=memberId
	 * :viewer@Delete=memberId
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

	/*
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json?key1=value1
	 */
	public void setProperty(String groupId, String key, String value) throws GroupModificationException {
		 setProperties(groupId, ImmutableMap.of(key, value));
	}

	/*
	 * POST http://localhost:8080/system/userManager/group/groupId.update.json?key1=value1&key2=value2...
	 */
	public void setProperties(String groupId, Map<String,String> properties) throws GroupModificationException {
		PostMethod method = new PostMethod(url.toString() + getUpdateURI(groupId));
		for (Entry<String,String> entry: properties.entrySet()){
			method.setParameter(entry.getKey(), entry.getValue());
		}
		method.setParameter(CHARSET_PARAM, UTF_8);
		try {
			if (!dryrun){
        		NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
                for (Entry<String,String> entry: properties.entrySet()){
                	AuditLogUtils.audit(AuditLogUtils.GROUP_MODIFIED, null, groupId,
                			entry.getKey() + "=" + entry.getValue(), AuditLogUtils.SUCCESS);
                	log.info("Set " + groupId + " : "+ entry.getKey() + "=" + entry.getValue());
                }
			}
		}
		catch (GrouperException ge){
        	AuditLogUtils.audit(AuditLogUtils.GROUP_MODIFIED, null, groupId,
        			properties.toString(), AuditLogUtils.FAILURE);
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

	public List<String> getRoles(String worldId){
		List<String> roles = new ArrayList<String>();
		GetMethod get = new GetMethod(url + GROUP_PATH_PREFIX + "/" + worldId + ".json");
		try {
			JSONObject json = NakamuraHttpUtils.http(NakamuraHttpUtils.getHttpClient(url, username, password), get);
			if (json != null){
				JSONArray jsonRoles = json.getJSONArray("sakai:roles");
				for (Object jRole : jsonRoles){
					JSONObject j = (JSONObject)jRole;
					roles.add((String)j.get("id"));
				}
			}
		}
		catch (GroupModificationException gme){
			log.error("wtf? on a get?", gme);
		}
		return roles;
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
		method.addParameter("sakai:excludeSearch", WorldConstants.TRUE);
		method.addParameter("sakai:group-description", description);
		method.addParameter("sakai:group-title", nakamuraGroupId + "(" + role + ")");
		method.addParameter("sakai:pseudoGroup", WorldConstants.TRUE);
		method.addParameter("sakai:pseudogroupparent", groupIdAdapter.getPseudoGroupParent(nakamuraGroupId));
		method.setParameter("sakai:group-joinable", WorldConstants.NO);
		method.addParameter(GROUPER_NAME_PROP, groupName.substring(0, groupName.lastIndexOf(":") + 1 )
											+ nakamuraGroupId.substring(nakamuraGroupId.lastIndexOf("-") + 1));
		method.setParameter(GROUPER_PROVISIONED_PROP, WorldConstants.TRUE);

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
