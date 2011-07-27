package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Iterator;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraGroupAdapter;
import org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupAlreadyExistsException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

import com.google.common.collect.ImmutableMap;

import edu.internet2.middleware.grouper.Group;

/**
 * Synchronize Simple group information stored in Grouper by reading the
 * change log and provisioning OAE accordingly.
 *
 * This should only be used to manage "Simple Groups" as they're known in the
 * Sakai OAE interface.
 *
 * @see edu.internet2.middleware.grouper.changelog.*
 */
public class HttpSimpleGroupAdapter extends BaseGroupAdapter implements NakamuraGroupAdapter {

	private Log log = LogFactory.getLog(HttpSimpleGroupAdapter.class);

	// Maps grouper gouperName -> nakamura groupId
	protected SimpleGroupIdAdapter groupIdAdapter;

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
		method.setParameter("grouper:name", group.getName());

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
	    String contentString = "{\"library\":{\"_title\":\"Library\",\"_order\":0,\"_nonEditable\":true,\"_view\":\"[\\\"everyone\\\",\\\"anonymous\\\",\\\"-member\\\"]\",\"_edit\":\"[\\\"-manager\\\"]\",\"_pid\":\"#{library_doc_hash}\"},\"participants\":{\"_title\":\"Participants\",\"_order\":1,\"_nonEditable\":true,\"_view\":\"[\\\"everyone\\\",\\\"anonymous\\\",\\\"-member\\\"]\",\"_edit\":\"[\\\"-manager\\\"]\",\"_pid\":\"#{participants_doc_hash}\"}}";
	    contentString = contentString.replaceAll("#\\{library_doc_hash\\}", libraryDocHash);
	    contentString = contentString.replaceAll("#\\{participants_doc_hash\\}", participantsDocHash);
	    content.put("structure0", contentString);
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

			for (String deleteId: new String[] { memberPsuedoGroupId, managerPsuedoGroupId, parentGroupId }){
				HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
				PostMethod method = new PostMethod(url.toString() + getDeleteURI(deleteId));
				method.addParameter(":operation", "delete");
				method.addParameter("go", "1");
				try {
					http(client, method);
				}
				catch (GroupModificationException e) {
					if (e.code != HttpStatus.SC_NOT_FOUND){
						throw e;
					}
				}
				log.info("Deleted " + deleteId + " for " + groupName);
			}
		}
	}

	public void addMembership(String groupId, String groupName, String memberId)
	throws GroupModificationException {
		addMembership(groupIdAdapter.getNakamuraGroupId(groupName), memberId);
	}

	public void deleteMembership(String groupId, String groupName, String memberId)
	throws GroupModificationException {
		deleteMembership(groupIdAdapter.getNakamuraGroupId(groupName), memberId);
	}

	public void setGroupIdAdapter(SimpleGroupIdAdapter groupIdAdapter) {
		this.groupIdAdapter = groupIdAdapter;
	}
}