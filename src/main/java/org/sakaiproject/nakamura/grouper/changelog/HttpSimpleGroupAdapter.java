package org.sakaiproject.nakamura.grouper.changelog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.UUID;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.HttpHeaders;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraGroupAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupAlreadyExistsException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;
import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;
import com.google.common.collect.ImmutableMap;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.exception.GrouperException;

/**
 * Synchronize Simple group information stored in Grouper by reading the
 * change log and provisioning OAE accordingly.
 *
 * This should only be used to manage "Simple Groups" as they're known in the
 * Sakai OAE interface.
 *
 * @see edu.internet2.middleware.grouper.changelog.*
 */
public class HttpSimpleGroupAdapter implements NakamuraGroupAdapter {

	private Log log = LogFactory.getLog(HttpSimpleGroupAdapter.class);

	// Nakamura URL
	protected URL url;
	protected String username;
	protected String password;

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

	// Maps grouper gouprName -> nakamura groupId
	protected GroupIdAdapter groupIdAdapter;

	private boolean createUsers = false;

	/**
	 * POST to http://localhost:8080/system/userManager/group.create.json
	 * @see org.sakaiproject.nakamura.user.servlet.CreateSakaiGroupServlet
	 */
	public void createGroup(Group group) throws GroupModificationException {

		String nakamuraGroupId = groupIdAdapter.getNakamuraGroupId(group.getName());
		if(log.isDebugEnabled()){
			log.debug(group.getName() + " converted to " + nakamuraGroupId + " for nakamura.");
		}

		String creator = "admin";

		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = new PostMethod(url.toString() + GROUP_CREATE_URI);

		String parentGroupId = getPseudoGroupParent(nakamuraGroupId);
		String managerGroupId = parentGroupId + "-" + SimpleGroupEsbConsumer.MANAGER_SUFFIX;
		String memberGroupId = parentGroupId + "-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX;
		for (String psuedoGroupId: new String[]{ managerGroupId, memberGroupId }){
			// --------------------------------------------------------------------
			// POST - create the members and managers
			try {
				createPseudoGroup(psuedoGroupId, group);
				log.info("Created the pseudo group " + psuedoGroupId);
			}
			catch (GroupAlreadyExistsException gme){
				log.debug(psuedoGroupId + " already exists. No worries.");
			}
		}

		method = new PostMethod(url + GROUP_CREATE_URI);
		method.setParameter("_charset_", "utf-8");
		method.setParameter(":name", parentGroupId);
		method.setParameter("sakai:group-title", parentGroupId);
		method.setParameter("sakai:group-description", parentGroupId);
		method.setParameter("sakai:group-id", parentGroupId);
		method.setParameter("sakai:joinRole", "student");
		method.setParameter("sakai:joinable", "yes");
		method.setParameter("sakai:roles", "[{\"id\":\"member\",\"title\":\"Member\",\"allowManage\":false},{\"id\":\"manager\",\"title\":\"Manager\",\"allowManage\":true}]");
		method.setParameter("sakai:template-id", "simplegroup");

		try {
			http(client, method);
		}
		catch (GroupAlreadyExistsException gme){
			log.debug(parentGroupId + " already exists. No worries.");
		}
		log.info("Created the parent group " + parentGroupId);

		// --------------------------------------------------------------------
		// POST 5 - updating the group managers
		JSONArray batchPosts = new JSONArray();

		for (String roleGroupId: new String[] { memberGroupId, managerGroupId, parentGroupId }){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			// courseId-managerRoleName will be a manager of roleName
			params.put(":manager", managerGroupId);
			params.put("_charset_", "utf-8");
			request.put("url", GROUP_PATH_PREFIX + "/" + roleGroupId + ".update.json");
			request.put("method", "POST");
			request.put("parameters", params);
			request.put("_charset_", "utf-8");
			batchPosts.add(request);
		}

		method = new PostMethod(url + BATCH_URI);
		JSONArray json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		http(client, method);
		log.debug("Updated the group managers.");

		// --------------------------------------------------------------------
		// POST 6 - updating the group members

		batchPosts.clear();

		JSONObject req1 = new JSONObject();
		JSONObject p1 = new JSONObject();
		p1.put(":member", creator);
		p1.put(":viewer", creator);
		p1.put("_charset_", "utf-8");
		req1.put("url", GROUP_PATH_PREFIX + "/" + managerGroupId + ".update.json");
		req1.put("method", "POST");
		req1.put("parameters", p1);
		req1.put("_charset_", "utf-8");
		batchPosts.add(req1);

		for (String roleGroupId: new String[] { managerGroupId, memberGroupId }){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			params.put(":member", roleGroupId);
			params.put(":viewer", roleGroupId);
			params.put("_charset_", "utf-8");
			request.put("url", GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
			request.put("method", "POST");
			request.put("parameters", params);
			request.put("_charset_", "utf-8");
			batchPosts.add(request);
		}

		method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		http(client, method);
		log.debug("Updated the group members.");

		// --------------------------------------------------------------------
		// POST 7 - updating group visibility, joinability and permissions

		batchPosts.clear();
		req1 = new JSONObject();
		p1 = new JSONObject();
		p1.put("rep:group-viewers@Delete", "");
		p1.put("sakai:group-visible", "public");
		p1.put("sakai:group-joinable", "yes");
		p1.put("_charset_", "utf-8");
		req1.put("url", GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
		req1.put("method", "POST");
		req1.put("parameters", p1);
		req1.put("_charset_", "utf-8");
		batchPosts.add(req1);

		JSONObject req2 = new JSONObject();
		JSONObject p2 = new JSONObject();
		p2.put("principalId", "everyone");
		p2.put("privilege@jcr:read", "granted");
		p2.put("_charset_", "utf-8");
		req2.put("url", "/~" + parentGroupId + ".modifyAce.html");
		req2.put("method", "POST");
		req2.put("parameters", p2);
		req2.put("_charset_", "utf-8");
		batchPosts.add(req2);

		JSONObject req3 = new JSONObject();
		JSONObject p3 = new JSONObject();
		p3.put("principalId", "anonymous");
		p3.put("privilege@jcr:read", "granted");
		p3.put("_charset_", "utf-8");
		req3.put("url", "/~" + parentGroupId + ".modifyAce.html");
		req3.put("method", "POST");
		req3.put("parameters", p3);
		req3.put("_charset_", "utf-8");
		batchPosts.add(req3);

		method = new PostMethod(url + BATCH_URI);
		json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		http(client, method);
		log.debug("Updated the visibilty and joinability.");

		// --------------------------------------------------------------------
		// POST 8 - creating initial sakai docs
		batchPosts.clear();
	    JSONObject libraryP = new JSONObject();
	    libraryP.put("sakai:pooled-content-file-name", "Library");
	    libraryP.put("sakai:description", "");
	    libraryP.put("sakai:permissions", "public");
	    libraryP.put("sakai:copyright", "creativecommons");
	    libraryP.put("structure0", "{\"library\":{\"_ref\":\"id9867543247\",\"_order\":0,\"_nonEditable\":true,\"_title\":\"Library\",\"main\":{\"_ref\":\"id9867543247\",\"_order\":0,\"_nonEditable\":true,\"_title\":\"Library\"}}}");
	    libraryP.put("sakai:custom-mimetype", "x-sakai/document");
	    libraryP.put("_charset_", "utf-8");

	    req1 = new JSONObject();
	    req1.put("url", CREATE_FILE_URI);
	    req1.put("method", "POST");
	    req1.put("parameters", libraryP);
	    req1.put("_charset_", "utf-8");
	    batchPosts.add(req1);

	    JSONObject participantsP = new JSONObject();
	    participantsP.put("sakai:pooled-content-file-name", "Participants");
	    participantsP.put("sakai:description", "");
	    participantsP.put("sakai:permissions", "public");
	    participantsP.put("sakai:copyright", "creativecommons");
	    participantsP.put("structure0", "{\"participants\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_nonEditable\":true,\"_title\":\"Participants\",\"main\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_nonEditable\":true,\"_title\":\"Participants\"}}}");
	    participantsP.put("sakai:custom-mimetype", "x-sakai/document");
	    participantsP.put("_charset_", "utf-8");

	    req2 = new JSONObject();
	    req2.put("url", CREATE_FILE_URI);
	    req2.put("method", "POST");
	    req2.put("parameters", participantsP);
	    req2.put("_charset_", "utf-8");
	    batchPosts.add(req2);

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    JSONObject createDocsResponse = http(client, method);

	    // Go through the response and get the document UUID for the library and participants items.
	    String libraryDocHash = null;
	    String participantsDocHash = null;

	    if (createDocsResponse != null){
	    	try {
	    		JSONArray docResults = createDocsResponse.getJSONArray("results");
	    		Iterator<JSONObject> itr = docResults.iterator();
	    		while(itr.hasNext()){
	    			JSONObject result = itr.next();
	    			JSONObject body = result.getJSONObject("body");
	    			JSONObject contentItem = body.getJSONObject("_contentItem");
	    			JSONObject item = contentItem.getJSONObject("item");
	    			String poolId = (String)contentItem.get("poolId");
	    			if (item.getString("sakai:pooled-content-file-name").equals("Library")){
	    				libraryDocHash = poolId;
	    			}
	    			else if (item.getString("sakai:pooled-content-file-name").equals("Participants")){
	    				participantsDocHash = poolId;
	    			}
	    		}
	    		log.debug("Created the initial docs");
	    	}
	    	catch(Exception e){
	    		log.error("Creating the initial docs: error parsing the response into JSON.");
	    	}
	    }

	    // --------------------------------------------------------------------
	    // POST 9 - importing sakai docs content

	    batchPosts.clear();

	    JSONObject libraryParams = new JSONObject();
	    libraryParams.put(":operation", "import");
	    libraryParams.put(":contentType", "json");
	    libraryParams.put(":replace", true);
	    libraryParams.put(":replaceProperties", true);
	    libraryParams.put("_charset_", "utf-8");
	    JSONObject libraryContent = new JSONObject();
	    libraryContent.put("id9867543247", JSONSerializer.toJSON(ImmutableMap.of("page", "<img id='widget_mylibrary_id1367865652332' class='widget_inline' style='display: block; padding: 10px; margin: 4px;' src='/devwidgets/mylibrary/images/mylibrary.png' data-mce-src='/devwidgets/mylibrary/images/mylibrary.png' data-mce-style='display: block; padding: 10px; margin: 4px;' border='1'><br></p>")));
	    libraryContent.put("id1367865652332", JSONSerializer.toJSON(ImmutableMap.of("mylibrary", ImmutableMap.of("groupid", parentGroupId))));
	    libraryParams.put(":content", libraryContent);

	    JSONObject libraryRequest = new JSONObject();
	    libraryRequest.put("url", "/p/" + libraryDocHash + ".resource");
	    libraryRequest.put("method", "POST");
	    libraryRequest.put("parameters", libraryParams);
	    libraryRequest.put("_charset", "utf-8");

	    JSONObject participantsParams = new JSONObject();
	    participantsParams.put(":operation", "import");
	    participantsParams.put(":contentType", "json");
	    participantsParams.put(":replace", true);
	    participantsParams.put(":replaceProperties", true);
	    participantsParams.put("_charset_", "utf-8");
	    JSONObject participantsContent = new JSONObject();
	    participantsContent.put("id6573920372", JSONSerializer.toJSON(ImmutableMap.of("page", "<img id='widget_participants_id439704665' class='widget_inline' style='display: block; padding: 10px; margin: 4px;' src='/devwidgets/participants/images/participants.png' data-mce-src='/devwidgets/participants/images/participants.png' data-mce-style='display: block; padding: 10px; margin: 4px;' border='1'><br></p>")));
	    participantsContent.put("id6573920372", JSONSerializer.toJSON(ImmutableMap.of("participants", ImmutableMap.of("groupid", parentGroupId))));
	    participantsParams.put(":content", participantsContent);

	    JSONObject participantsRequest = new JSONObject();
	    participantsRequest.put("url", "/p/" + participantsDocHash + ".resource");
	    participantsRequest.put("method", "POST");
	    participantsRequest.put("parameters", participantsParams);
	    participantsRequest.put("_charset", "utf-8");

	    batchPosts.add(libraryRequest);
	    batchPosts.add(participantsRequest);

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    http(client, method);

	    log.debug("Added initial content into the sakai documents.");

        // --------------------------------------------------------------------
	    // POST 10 - setting the global viewers and permissions on the sakai docs

	    batchPosts.clear();
		req1 = new JSONObject();
		req1.put("url", "/p/" + libraryDocHash + ".members.html");
		req1.put("method", "POST");
		req1.put("parameters", ImmutableMap.of(":viewer", new String[] {"everyone", "anonymous" }));
		req1.put("_charset_", "utf-8");
		batchPosts.add(req1);

		req2 = new JSONObject();
		p2 = new JSONObject();
		p2.put("principalId", new String[] {"everyone", "anonymous"});
		p2.put("privilege@jcr:read", "granted");
		p2.put("_charset_", "utf-8");
		req2.put("url", "/~" + libraryDocHash + ".modifyAce.html");
		req2.put("method", "POST");
		req2.put("parameters", p2);
		req2.put("_charset_", "utf-8");
		batchPosts.add(req2);

		req3 = new JSONObject();
		req3.put("url", "/~" + participantsDocHash + ".members.html");
		req3.put("method", "POST");
		req3.put("parameters", ImmutableMap.of(":viewer", new String[] {"everyone", "anonymous" }));
		req3.put("_charset_", "utf-8");
		batchPosts.add(req3);

		JSONObject req4 = new JSONObject();
		JSONObject p4 = new JSONObject();
		p4.put("principalId", new String[] {"everyone", "anonymous"});
		p4.put("privilege@jcr:read", "granted");
		p4.put("_charset_", "utf-8");
		req4.put("url", "/~" + participantsDocHash + ".modifyAce.html");
		req4.put("method", "POST");
		req4.put("parameters", p4);
		req4.put("_charset_", "utf-8");
		batchPosts.add(req4);

		method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    http(client, method);

	    log.debug("Set global viewers and perms on the sakai documents.");

        // --------------------------------------------------------------------
	    // POST 11 - setting the member viewer and manager viewer for the sakai docs

	    batchPosts.clear();
		req1 = new JSONObject();
		req1.put("url", "/p/" + libraryDocHash + ".members.html");
		req1.put("method", "POST");
		req1.put("parameters", ImmutableMap.of(":viewer", memberGroupId, "_charset_", "utf-8"));
		req1.put("_charset_", "utf-8");
		batchPosts.add(req1);

		req2 = new JSONObject();
		req2.put("url", "/~" + libraryDocHash + ".members.html");
		req2.put("method", "POST");
		req2.put("parameters", ImmutableMap.of(":viewer", managerGroupId, "_charset_", "utf-8"));
		req2.put("_charset_", "utf-8");
		batchPosts.add(req2);

		req3 = new JSONObject();
		req3.put("url", "/~" + participantsDocHash + ".members.html");
		req3.put("method", "POST");
		req3.put("parameters", ImmutableMap.of(":viewer", memberGroupId, "_charset_", "utf-8"));
		req3.put("_charset_", "utf-8");
		batchPosts.add(req3);

		req4 = new JSONObject();
		req4.put("url", "/~" + participantsDocHash + ".modifyAce.html");
		req4.put("method", "POST");
		req4.put("parameters", ImmutableMap.of(":viewer", managerGroupId, "_charset_", "utf-8"));
		req4.put("_charset_", "utf-8");
		batchPosts.add(req4);

		method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    http(client, method);

	    log.debug("Set the member viewer and manager viewer on the sakai documents.");

	    // --------------------------------------------------------------------
	    // POST 12 - setting the doc structure on the sakai docs
	    method = new PostMethod(url + "/~" + parentGroupId + "/docstructure");
	    JSONObject content = new JSONObject();
	    content.put("structure0", "{\"library\":{\"_title\":\"Library\",\"_order\":0,\"_nonEditable\":true,\"_view\":\"[\\\"everyone\\\",\\\"anonymous\\\",\\\"-member\\\"]\",\"_edit\":\"[\\\"-manager\\\"]\",\"_pid\":\"#{library_doc_hash}\"},\"participants\":{\"_title\":\"Participants\",\"_order\":1,\"_nonEditable\":true,\"_view\":\"[\\\"everyone\\\",\\\"anonymous\\\",\\\"-member\\\"]\",\"_edit\":\"[\\\"-manager\\\"]\",\"_pid\":\"#{participants_doc_hash}\"}}");
	    method.setParameter(":content", content.toString());
	    method.setParameter(":contentType", "json");
	    method.setParameter(":operation", "import");
	    method.setParameter(":replace", "true");
	    method.setParameter(":replaceProperties", "true");
	    method.setParameter("_charset_", "utf-8");

	    http(client, method);
	}

	/**
	 * Delete a group from sakai3
	 * curl -Fgo=1 http://localhost:8080/system/userManager/group/groupId.delete.json
	 */
	public void deleteGroup(String groupId, String groupName) throws GroupModificationException {

		String nakamuraGroupId = groupIdAdapter.getNakamuraGroupId(groupName);

		if (nakamuraGroupId.endsWith(SimpleGroupEsbConsumer.MEMBER_SUFFIX)){
			String parentGroupId = getPseudoGroupParent(nakamuraGroupId);
			String memberPsuedoGroupId = parentGroupId + "-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX;
			String managerPsuedoGroupId = parentGroupId + "-" + SimpleGroupEsbConsumer.MANAGER_SUFFIX;

			HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
			PostMethod method = new PostMethod(url.toString() + getDeleteURI(parentGroupId));
			method.addParameter("go", "1");
			http(client, method);
			log.info("Deleted " + parentGroupId + " for " + groupName);

			client = NakamuraHttpUtils.getHttpClient(url, username, password);
			method = new PostMethod(url.toString() + getDeleteURI(memberPsuedoGroupId));
			method.addParameter("go", "1");
			http(client, method);
			log.info("Deleted " + memberPsuedoGroupId + " for " + groupName);

			client = NakamuraHttpUtils.getHttpClient(url, username, password);
			method = new PostMethod(url.toString() + getDeleteURI(managerPsuedoGroupId));
			method.addParameter("go", "1");
			http(client, method);
			log.info("Deleted " + managerPsuedoGroupId + " for " + groupName);
		}
	}

	/**
	 * Add a subjectId to a group by POSTing to:
	 * http://localhost:8080/system/userManager/group/groupId.update.html :member=subjectId
	 */
	public void addMembership(String groupId, String groupName, String memberId)
			throws GroupModificationException {
        String nakamuraGroupId = groupIdAdapter.getNakamuraGroupId(groupName);
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
	public void deleteMembership(String groupId, String groupName, String memberId)
			throws GroupModificationException {
        String nakamuraGroupId = groupIdAdapter.getNakamuraGroupId(groupName);
        PostMethod method = new PostMethod(url.toString() + getUpdateURI(nakamuraGroupId));
        method.addParameter(":member@Delete", memberId);
        method.addParameter(":viewer@Delete", memberId);
        method.addParameter("_charset_", "utf-8");
        http(NakamuraHttpUtils.getHttpClient(url, username, password), method);
	    if (log.isInfoEnabled()){
	        log.info("SUCCESS: deleted subjectId=" + memberId + " from group=" + nakamuraGroupId );
	    }
	}

	/*************************************************************************
	 * Utility methods.*/

	public void createPseudoGroup(String nakamuraGroupId, Group group) throws GroupModificationException {
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
	 * Return the authorizableId of the parent group for this group.
	 * @param nakamuraGroupId
	 * @return
	 */
	public String getPseudoGroupParent(String nakamuraGroupId){
		int index = nakamuraGroupId.lastIndexOf("-");
		if (index == -1){
			return nakamuraGroupId;
		}
		else {
			return nakamuraGroupId.substring(0, index);
		}
	}

	/**
	 * @param groupId the id of the OAE group
	 * @return the URI to the delete operation.
	 */
	private String getDeleteURI(String groupId){
		return GROUP_PATH_PREFIX + "/" + groupId + ".delete.json";
	}

	/**
	 * @param groupId the id of the OAE group
	 * @return the URI to the update operation.
	 */
	private String getUpdateURI(String groupId){
		return GROUP_PATH_PREFIX + "/" + groupId + ".update.json";
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
	 * Create a user in OAE if it doesn't exist.
	 * @param userId
	 * @throws Exception
	 */
	private void createOAEUser(String userId) throws GroupModificationException {
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
					log.error("Could not parse JSON response.");
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
	private void errorToException(String errorMessage) throws GroupModificationException, GroupAlreadyExistsException {
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

	public void setGroupIdAdapter(GroupIdAdapter groupIdAdapter) {
		this.groupIdAdapter = groupIdAdapter;
	}

	public void setUrl(String urlString){
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
}