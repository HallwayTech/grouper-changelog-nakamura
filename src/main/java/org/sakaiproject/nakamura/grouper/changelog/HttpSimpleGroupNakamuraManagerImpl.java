package org.sakaiproject.nakamura.grouper.changelog;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;
import org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupAlreadyExistsException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.log.AuditLogUtils;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

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
public class HttpSimpleGroupNakamuraManagerImpl extends BaseHttpNakamuraManager implements NakamuraManager {

	private Log log = LogFactory.getLog(HttpSimpleGroupNakamuraManagerImpl.class);

	/**
	 * Create the groups and supporting objects to make a Simple Group in the Sakai OAE UX. 
	 * @see org.sakaiproject.nakamura.user.servlet.CreateSakaiGroupServlet
	 */
	public void createGroup(String groupName, String description) throws GroupModificationException {

		String nakamuraGroupId = groupIdAdapter.getGroupId(groupName);
		if(log.isDebugEnabled()){
			log.debug(groupName + " converted to " + nakamuraGroupId + " for nakamura.");
		}

		String creator = "admin";

		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = new PostMethod(url.toString() + GROUP_CREATE_URI);

		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
		String managerGroupId = parentGroupId + "-" + SimpleGroupEsbConsumer.MANAGER_SUFFIX;
		String memberGroupId = parentGroupId + "-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX;

		boolean allGroupsExisted = true;
		for (String pseudoGroupId: new String[]{ managerGroupId, memberGroupId }){
			// --------------------------------------------------------------------
			// POST - create the members and managers
			try {
				createPseudoGroup(pseudoGroupId, groupName, description);
				allGroupsExisted = false;
			}
			catch (GroupAlreadyExistsException gme){
				log.debug(pseudoGroupId + " already exists. No worries.");
			}
		}

		method = new PostMethod(url + GROUP_CREATE_URI);
		method.setParameter(CHARSET_PARAM, UTF_8);
		method.setParameter(":name", parentGroupId);
		method.setParameter("sakai:group-title", description);
		method.setParameter("sakai:group-description", description);
		method.setParameter("sakai:group-id", parentGroupId);
		method.setParameter("sakai:joinRole", "student");
		method.setParameter("sakai:joinable", "no");
		method.setParameter("sakai:roles", "[{\"id\":\"member\",\"title\":\"Member\",\"allowManage\":false},{\"id\":\"manager\",\"title\":\"Manager\",\"allowManage\":true}]");
		method.setParameter("sakai:template-id", "simplegroup");
		method.setParameter(GROUPER_NAME_PROP, groupName);
		method.setParameter(GROUPER_PROVISIONED_PROP, TRUE_VAL);

		try {
			NakamuraHttpUtils.http(client, method);
			allGroupsExisted = false;
			log.info("Created the parent group " + parentGroupId);
			AuditLogUtils.audit(AuditLogUtils.GROUP_CREATED, null, parentGroupId, description, AuditLogUtils.SUCCESS);
		}
		catch (GroupAlreadyExistsException gme){
			log.debug(parentGroupId + " already exists. No worries.");
			AuditLogUtils.audit(AuditLogUtils.GROUP_CREATED, null, parentGroupId, description, AuditLogUtils.FAILURE);
		}
		catch (GrouperException e){
			AuditLogUtils.audit(AuditLogUtils.GROUP_CREATED, null, parentGroupId, description, AuditLogUtils.FAILURE);
			throw e;
		}

		if (allGroupsExisted){
			log.info("All of the groups for the " + parentGroupId + " course existed already. The course is considered created.");
			return;
		}

		// --------------------------------------------------------------------
		// POST 5 - updating the group managers
		JSONArray batchPosts = new JSONArray();

		for (String roleGroupId: new String[] { memberGroupId, managerGroupId, parentGroupId }){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			// courseId-managerRoleName will be a manager of roleName
			params.put(MANAGER_PARAM, managerGroupId);
			params.put(CHARSET_PARAM, UTF_8);
			request.put(URL_PARAM, GROUP_PATH_PREFIX + "/" + roleGroupId + ".update.json");
			request.put(METHOD_PARAM, "POST");
			request.put(PARAMETERS_PARAM, params);
			request.put(CHARSET_PARAM, UTF_8);
			batchPosts.add(request);
		}

		method = new PostMethod(url + BATCH_URI);
		JSONArray json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		if(!dryrun){
	    	NakamuraHttpUtils.http(client, method);
		}
		log.debug("Updated the group managers.");

		// --------------------------------------------------------------------
		// POST 6 - updating the group members

		batchPosts.clear();

		JSONObject req1 = new JSONObject();
		JSONObject p1 = new JSONObject();
		p1.put(MEMBER_PARAM, creator);
		p1.put(VIEWER_PARAM, creator);
		p1.put(CHARSET_PARAM, UTF_8);
		req1.put(URL_PARAM, GROUP_PATH_PREFIX + "/" + managerGroupId + ".update.json");
		req1.put(METHOD_PARAM, "POST");
		req1.put(PARAMETERS_PARAM, p1);
		req1.put(CHARSET_PARAM, UTF_8);
		batchPosts.add(req1);

		for (String roleGroupId: new String[] { managerGroupId, memberGroupId }){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			params.put(MEMBER_PARAM, roleGroupId);
			params.put(VIEWER_PARAM, roleGroupId);
			params.put(CHARSET_PARAM, UTF_8);
			request.put(URL_PARAM, GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
			request.put(METHOD_PARAM, "POST");
			request.put(PARAMETERS_PARAM, params);
			request.put(CHARSET_PARAM, UTF_8);
			batchPosts.add(request);
		}

		method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		if(!dryrun){
	    	NakamuraHttpUtils.http(client, method);
		}
		log.debug("Updated the group members.");

		// --------------------------------------------------------------------
		// POST 7 - updating group visibility, joinability and permissions

		batchPosts.clear();
		req1 = new JSONObject();
		p1 = new JSONObject();
		p1.put("rep:group-viewers@Delete", "");
		p1.put("sakai:group-visible", "public");
		p1.put("sakai:group-joinable", "yes");
		p1.put(CHARSET_PARAM, UTF_8);
		req1.put(URL_PARAM, GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
		req1.put(METHOD_PARAM, "POST");
		req1.put(PARAMETERS_PARAM, p1);
		req1.put(CHARSET_PARAM, UTF_8);
		batchPosts.add(req1);

		JSONObject req2 = new JSONObject();
		JSONObject p2 = new JSONObject();
		p2.put("principalId", "everyone");
		p2.put("privilege@jcr:read", "granted");
		p2.put(CHARSET_PARAM, UTF_8);
		req2.put(URL_PARAM, "/~" + parentGroupId + ".modifyAce.html");
		req2.put(METHOD_PARAM, "POST");
		req2.put(PARAMETERS_PARAM, p2);
		req2.put(CHARSET_PARAM, UTF_8);
		batchPosts.add(req2);

		JSONObject req3 = new JSONObject();
		JSONObject p3 = new JSONObject();
		p3.put("principalId", "anonymous");
		p3.put("privilege@jcr:read", "granted");
		p3.put(CHARSET_PARAM, UTF_8);
		req3.put(URL_PARAM, "/~" + parentGroupId + ".modifyAce.html");
		req3.put(METHOD_PARAM, "POST");
		req3.put(PARAMETERS_PARAM, p3);
		req3.put(CHARSET_PARAM, UTF_8);
		batchPosts.add(req3);

		method = new PostMethod(url + BATCH_URI);
		json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		if(!dryrun){
	    	NakamuraHttpUtils.http(client, method);
		}
		log.debug("Updated the visibilty and joinability.");

		method = new PostMethod(url + "/~" + parentGroupId + "/docstructure");

		method.setParameter(OPERATION_PARAM, "import");
		method.setParameter(":contentType", "json");
		method.setParameter(":replace", "true");
		method.setParameter(":replaceProperties", "true");
		method.setParameter(CHARSET_PARAM, UTF_8);
		method.setParameter(":content", "{ \"structure0\":\"{}\"}");
		method.setParameter(CHARSET_PARAM, UTF_8);
		if(!dryrun){
			NakamuraHttpUtils.http(client, method);
		}

	    log.info("Done creating the Sakai OAE group " + parentGroupId);
	}
}