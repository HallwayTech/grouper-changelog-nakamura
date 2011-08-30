package org.sakaiproject.nakamura.grouper.changelog;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraGroupAdapter;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupAlreadyExistsException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

/**
 * Provision Course Groups in Sakai OAE over HTTP according to the Grouper changelog.
 */
public class HttpCourseAdapter extends BaseGroupAdapter implements NakamuraGroupAdapter {

	private Log log = LogFactory.getLog(HttpCourseAdapter.class);

	private static final String DEFAULT_COURSE_TEMPLATE = "mathcourse";

	/**
	 * Create the full set of objects that are necessary to have a working
	 * course in Sakai OAE.
	 *
	 * Written on the back of the amazing John King who pulled out all of this
	 * for nakamura/testscripts/SlingRuby/dataload/full_group_creator.rb
	 * @throws GroupModificationException
	 */
	@Override
	public void createGroup(String groupName, String description) throws GroupModificationException {

		String nakamuraGroupId = groupIdAdapter.getGroupId(groupName);
		log.debug(groupName + " converted to " + nakamuraGroupId + " for nakamura.");

		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = null;

		// TODO - Fix this. Either key off of the lecturers group and grab the
		// first member or use an attribute.
		// We might not have members at add time.
		String creator = username;

		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
		String lecturerGroupId = parentGroupId + "-lecturer";
		String taGroupId = parentGroupId + "-ta";
		String studentGroupId = parentGroupId + "-student";

		String[] parentAndPsuedoGroupIds = new String[] { parentGroupId, lecturerGroupId, taGroupId, studentGroupId };

		boolean allGroupsExisted = true;
		for (String pseudoGroupId: new String[]{ lecturerGroupId, taGroupId, studentGroupId }){
			// --------------------------------------------------------------------
			// POST - create the lecturer, ta, and student.
			try {
				createPseudoGroup(pseudoGroupId, groupName, description);
				allGroupsExisted = false;
			}
			catch (GroupAlreadyExistsException gme){
				log.debug(pseudoGroupId + " already exists. No worries.");
			}
		}

		// --------------------------------------------------------------------
		// POST 4 - creating the main group
		method = new PostMethod(url + GROUP_CREATE_URI);
		method.setParameter(CHARSET_PARAM, UTF_8);
		method.setParameter(":name", parentGroupId);
		method.setParameter("sakai:group-title", description);
		method.setParameter("sakai:group-description", description);
		method.setParameter("sakai:group-id", parentGroupId);
		method.setParameter("sakai:category", "courses");
		method.setParameter("sakai:templateid", DEFAULT_COURSE_TEMPLATE);
		method.setParameter("sakai:joinRole", "student");
		method.setParameter("sakai:joinable", "no");
		method.setParameter("sakai:roles", "[{\"id\":\"student\",\"roleTitle\":\"Students\",\"title\":\"Student\",\"allowManage\":false},{\"id\":\"ta\",\"roleTitle\":\"Teaching Assistants\",\"title\":\"Teaching Assistant\",\"allowManage\":true},{\"id\":\"lecturer\",\"roleTitle\":\"Lecturers\",\"title\":\"Lecturer\",\"allowManage\":true}]");
		method.setParameter(GROUPER_NAME_PROP, groupName);
		method.setParameter(GROUPER_PROVISIONED_PROP, TRUE_VAL);
		try {
			if (!dryrun){
				NakamuraHttpUtils.http(client, method);
				allGroupsExisted = false;
				log.info("Created the parent group " + parentGroupId);
			}
		}
		catch (GroupAlreadyExistsException gme){
			log.debug(parentGroupId + " already exists. No worries.");
		}

		if (allGroupsExisted){
			log.info("All of the groups for the " + parentGroupId + " course existed already. The course is considered created.");
			return;
		}

		// --------------------------------------------------------------------
		// POST 5 - updating the group managers
		JSONArray batchPosts = new JSONArray();

		for (String managerGroupId: new String[] { taGroupId, lecturerGroupId }){
			JSONObject request;
			JSONObject params;
			for (String roleGroupId: new String[] { studentGroupId, taGroupId, lecturerGroupId }){
				request = new JSONObject();
				params = new JSONObject();
				// courseId-managerRoleName will be a manager of roleName
				params.put(":manager", managerGroupId);
				params.put(CHARSET_PARAM, UTF_8);
				request.put("url", GROUP_PATH_PREFIX + "/" + roleGroupId + ".update.json");
				request.put("method", "POST");
				request.put("parameters", params);
				request.put(CHARSET_PARAM, UTF_8);
				batchPosts.add(request);
			}

			// managerGroupId will be a manager of courseId
			request = new JSONObject();
			params = new JSONObject();
			params.put(":manager", managerGroupId);
			params.put(CHARSET_PARAM, UTF_8);
			request.put("url", GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
			request.put("method", "POST");
			request.put("parameters", params);
			request.put(CHARSET_PARAM, UTF_8);
			batchPosts.add(request);
		}

		method = new PostMethod(url + BATCH_URI);
		JSONArray json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		method.setParameter(CHARSET_PARAM, UTF_8);
		if (!dryrun){
			NakamuraHttpUtils.http(client, method);
		}
		log.debug("Updated the group managers.");

		// --------------------------------------------------------------------
		// POST 6 - updating the group members
	    batchPosts.clear();

	    JSONObject req1 = new JSONObject();
		JSONObject p1 = new JSONObject();
		p1.put(":member", creator);
		p1.put(":viewer", creator);
		p1.put(CHARSET_PARAM, UTF_8);
		req1.put("url", GROUP_PATH_PREFIX + "/" + lecturerGroupId + ".update.json");
		req1.put("method", "POST");
		req1.put("parameters", p1);
		req1.put(CHARSET_PARAM, UTF_8);
		batchPosts.add(req1);

		for (String roleGroupId: new String[] { studentGroupId, taGroupId, lecturerGroupId }){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			params.put(":member", roleGroupId);
			params.put(":viewer", roleGroupId);
			params.put(CHARSET_PARAM, UTF_8);
			request.put("url", GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
			request.put("method", "POST");
			request.put("parameters", params);
			request.put(CHARSET_PARAM, UTF_8);
			batchPosts.add(request);
		}
		for (String roleName: new String[] { "ta", "lecturer" }){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			params.put(":viewer", parentGroupId + "-student");
			params.put(CHARSET_PARAM, UTF_8);
			request.put("url", GROUP_PATH_PREFIX + "/" + parentGroupId + "-" + roleName + ".update.json");
			request.put("method", "POST");
			request.put("parameters", params);
			request.put(CHARSET_PARAM, UTF_8);
			batchPosts.add(req1);
		}

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		method.setParameter(CHARSET_PARAM, UTF_8);
		if (!dryrun){
			NakamuraHttpUtils.http(client, method);
		}
		log.debug("Updated the group members.");

		// --------------------------------------------------------------------
		// POST 7 - updating group visibility, joinability and permissions
		batchPosts.clear();
		JSONArray everyoneAnonymous = new JSONArray();
		everyoneAnonymous.add("everyone");
		everyoneAnonymous.add("anonymous");
		for (String gId : parentAndPsuedoGroupIds){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			params.put(":viewer", parentGroupId);
			params.put(":viewer@Delete", everyoneAnonymous);
			params.put("sakai:group-visible","members-only");
			params.put("sakai:group-joinable","no");
			params.put(CHARSET_PARAM, UTF_8);

			request.put("url", GROUP_PATH_PREFIX + "/" + gId + ".update.json");
			request.put("method", "POST");
			request.put("parameters", params);
			request.put(CHARSET_PARAM, UTF_8);
			batchPosts.add(request);
		}

		method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    method.setParameter(CHARSET_PARAM, UTF_8);
	    if (!dryrun){
            NakamuraHttpUtils.http(client, method);
	    }

		log.debug("Updated the visibilty and joinability.");

		method = new PostMethod(url + "/~" + parentGroupId + "/docstructure");

		method.setParameter(":operation", "import");
		method.setParameter(":contentType", "json");
		method.setParameter(":replace", "true");
		method.setParameter(":replaceProperties", "true");
		method.setParameter(":content", "{ \"structure0\":\"{}\"}");
		method.setParameter(CHARSET_PARAM, UTF_8);
		if(!dryrun){
			NakamuraHttpUtils.http(client, method);
		}
		log.debug("Imported the docstructure.");

	    log.info("Successfully created the course in sakai for " + parentGroupId);
	}

	@Override
	public void deleteGroup(String groupId, String groupName)
			throws GroupModificationException {

		String parentGroupId = groupIdAdapter.getPseudoGroupParent(groupId);
		log.info("Deleting course group " + parentGroupId);
		String lecturerGroupId = parentGroupId + "-lecturer";
		String taGroupId = parentGroupId + "-ta";
		String studentGroupId = parentGroupId + "-student";

		for (String deleteId: new String[] { lecturerGroupId, taGroupId, studentGroupId, parentGroupId }){
			HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
			PostMethod method = new PostMethod(url.toString() + getDeleteURI(deleteId));
			method.addParameter(":operation", "delete");
			method.addParameter("go", "1");
			try {
				if(!dryrun){
					NakamuraHttpUtils.http(client, method);
				}
			}
			catch (GroupModificationException e) {
				if (e.code != HttpStatus.SC_NOT_FOUND){
					throw e;
				}
			}
			log.info("Deleted " + deleteId);
		}


	}
}
