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
import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

import com.google.common.collect.ImmutableMap;

import edu.internet2.middleware.grouper.Group;

/**
 * Provision courses in Sakai OAE over HTTP according to the Grouper changelog
 */
public class HttpCourseAdapter extends BaseGroupAdapter implements NakamuraGroupAdapter {

	private Log log = LogFactory.getLog(HttpCourseAdapter.class);

	private static final String DEFAULT_COURSE_TEMPLATE = "mathcourse";

	// Maps grouper gouperName -> nakamura groupId
	protected GroupIdAdapter groupIdAdapter;

	/**
	 * Create the full set of objects that are necessary to have a working
	 * course in Sakai OAE.
	 *
	 * Written on the back of the amazing John King who pulled out all of this
	 * for nakamura/testscripts/SlingRuby/dataload/full_group_creator.rb
	 * @throws GroupModificationException
	 */
	@Override
	public void createGroup(Group group) throws GroupModificationException {

		String nakamuraGroupId = groupIdAdapter.getNakamuraGroupId(group.getName());
		log.debug(group.getName() + " converted to " + nakamuraGroupId + " for nakamura.");

		HttpClient client = NakamuraHttpUtils.getHttpClient(url, username, password);
		PostMethod method = null;

		// TODO - Fix this. Either key off of the lecturers group and grab the
		// first member or use an attribute.
		// We might not have members at add time.
		String creator = "admin";

		String parentGroupId = getPseudoGroupParent(nakamuraGroupId);
		String lecturerGroupId = parentGroupId + "-lecturer";
		String taGroupId = parentGroupId + "-ta";
		String studentGroupId = parentGroupId + "-student";

		for (String psuedoGroupId: new String[]{ lecturerGroupId, taGroupId, studentGroupId }){
			// --------------------------------------------------------------------
			// POST - create the lecturer, ta, and student.
			try {
				createPseudoGroup(psuedoGroupId, group);
				log.info("Created the pseudo group " + psuedoGroupId);
			}
			catch (GroupAlreadyExistsException gme){
				log.debug(psuedoGroupId + " already exists. No worries.");
			}
		}

		// --------------------------------------------------------------------
		// POST 4 - creating the main group
		method = new PostMethod(url + GROUP_CREATE_URI);
		method.setParameter("_charset_", "utf-8");
		method.setParameter(":name", parentGroupId);
		method.setParameter("sakai:group-title", parentGroupId);
		method.setParameter("sakai:group-description", parentGroupId);
		method.setParameter("sakai:group-id", parentGroupId);
		method.setParameter("sakai:category", "courses");
		method.setParameter("sakai:templateid", DEFAULT_COURSE_TEMPLATE);
		method.setParameter("sakai:joinRole", "student");
		method.setParameter("sakai:joinable", "yes");
		method.setParameter("sakai:roles", "[{\"id\":\"student\",\"roleTitle\":\"Students\",\"title\":\"Student\",\"allowManage\":false},{\"id\":\"ta\",\"roleTitle\":\"Teaching Assistants\",\"title\":\"Teaching Assistant\",\"allowManage\":true},{\"id\":\"lecturer\",\"roleTitle\":\"Lecturers\",\"title\":\"Lecturer\",\"allowManage\":true}]");
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

		for (String managerGroupId: new String[] { taGroupId, lecturerGroupId }){
			JSONObject request;
			JSONObject params;
			for (String roleGroupId: new String[] { studentGroupId, taGroupId, lecturerGroupId }){
				request = new JSONObject();
				params = new JSONObject();
				// courseId-managerRoleName will be a manager of roleName
				params.put(":manager", managerGroupId);
				params.put("_charset_", "utf-8");
				request.put("url", GROUP_PATH_PREFIX + "/" + roleGroupId + ".update.json");
				request.put("method", "POST");
				request.put("parameters", params);
				request.put("_charset_", "utf-8");
				batchPosts.add(request);
			}

			// managerGroupId will be a manager of courseId
			request = new JSONObject();
			params = new JSONObject();
			params.put(":manager", managerGroupId);
			params.put("_charset_", "utf-8");
			request.put("url", GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
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
		req1.put("url", GROUP_PATH_PREFIX + "/" + lecturerGroupId + ".update.json");
		req1.put("method", "POST");
		req1.put("parameters", p1);
		req1.put("_charset_", "utf-8");
		batchPosts.add(req1);

		for (String roleGroupId: new String[] { studentGroupId, taGroupId, lecturerGroupId }){
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
		for (String roleName: new String[] { "ta", "lecturer" }){
			JSONObject request = new JSONObject();
			JSONObject params = new JSONObject();
			params.put(":viewer", parentGroupId + "-student");
			params.put("_charset_", "utf-8");
			request.put("url", GROUP_PATH_PREFIX + "/" + parentGroupId + "-" + roleName + ".update.json");
			request.put("method", "POST");
			request.put("parameters", params);
			request.put("_charset_", "utf-8");
			batchPosts.add(req1);
		}

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
		method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
		http(client, method);
		log.debug("Updated the group members.");

		// --------------------------------------------------------------------
		// POST 7 - updating group visibility, joinability and permissions

		method = new PostMethod(url + GROUP_PATH_PREFIX + "/" + parentGroupId + ".update.json");
		method.setParameter("rep:group-viewers@Delete", "");
		method.setParameter("sakai:group-visible", "public");
		method.setParameter("sakai:group-joinable", "yes");
		method.setParameter("_charset_", "utf-8");
		http(client, method);
		log.debug("Updated the visibilty and joinability.");

		// --------------------------------------------------------------------
		// POST 8 - creating initial sakai docs
	    batchPosts.clear();
	    JSONObject syllabusP = new JSONObject();
	    syllabusP.put("sakai:pooled-content-file-name", "Syllabus");
	    syllabusP.put("sakai:description", "");
	    syllabusP.put("sakai:permissions", "everyone");
	    syllabusP.put("sakai:copyright", "creativecommons");
	    syllabusP.put("structure0", "{\"week1\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_title\":\"Week 1\",\"main\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_title\":\"Week 1\"}},\"week2\":{\"_ref\":\"id569856425\",\"_title\":\"Week 2\",\"_order\":1,\"main\":{\"_ref\":\"id569856425\",\"_order\":0,\"_title\":\"Week 2\"}},\"week3\":{\"_ref\":\"id647321988\",\"_title\":\"Week 3\",\"_order\":2,\"main\":{\"_ref\":\"id647321988\",\"_order\":0,\"_title\":\"Week 3\"}}}");
	    syllabusP.put("sakai:custom-mimetype", "x-sakai/document");
	    syllabusP.put("_charset_", "utf-8");

	    req1 = new JSONObject();
	    req1.put("url", CREATE_FILE_URI);
	    req1.put("method", "POST");
	    req1.put("parameters", syllabusP);
	    req1.put("_charset_", "utf-8");
	    batchPosts.add(req1);

	    JSONObject contactP = new JSONObject();
	    contactP.put("sakai:pooled-content-file-name", "Contact us");
	    contactP.put("sakai:description", "");
	    contactP.put("sakai:permissions", "private");
	    contactP.put("sakai:copyright", "creativecommons");
	    contactP.put("structure0", "{\"about\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_title\":\"About\",\"main\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_title\":\"About\"}},\"prospective\":{\"_ref\":\"id373710599\",\"_title\":\"Prospective Students\",\"_order\":1,\"main\":{\"_ref\":\"id373710599\",\"_order\":0,\"_title\":\"Prospective Students\"}}}");
	    contactP.put("sakai:custom-mimetype", "x-sakai/document");
	    contactP.put("_charset_", "utf-8");

	    JSONObject req2 = new JSONObject();
	    req2.put("url", CREATE_FILE_URI);
	    req2.put("method", "POST");
	    req2.put("parameters", contactP);
	    req2.put("_charset_", "utf-8");
	    batchPosts.add(req2);

	    JSONObject orgNotesP = new JSONObject();
	    orgNotesP.put("sakai:pooled-content-file-name", "Organization Notes");
	    orgNotesP.put("sakai:description", "");
	    orgNotesP.put("sakai:permissions", "private");
	    orgNotesP.put("sakai:copyright", "creativecommons");
	    orgNotesP.put("structure0", "{\"organizationnotes\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_title\":\"Organization Notes\",\"main\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_title\":\"Organization Notes\"}}}");
	    orgNotesP.put("sakai:custom-mimetype", "x-sakai/document");
	    orgNotesP.put("_charset_", "utf-8");

	    JSONObject req3 = new JSONObject();
	    req3.put("url", CREATE_FILE_URI);
	    req3.put("method", "POST");
	    req3.put("parameters", orgNotesP);
	    req3.put("_charset_", "utf-8");
	    batchPosts.add(req3);

	    JSONObject wikiP = new JSONObject();
	    wikiP.put("sakai:pooled-content-file-name", "Student Wiki");
	    wikiP.put("sakai:description", "");
	    wikiP.put("sakai:permissions", "private");
	    wikiP.put("sakai:copyright", "creativecommons");
	    wikiP.put("structure0", "{\"studentwiki\":{\"_ref\":\"id849031890418\",\"_order\":0,\"_title\":\"Student Wiki\",\"main\":{\"_ref\":\"id849031890418\",\"_order\":0,\"_title\":\"Organization Notes\"}}}");
	    wikiP.put("sakai:custom-mimetype", "x-sakai/document");
	    wikiP.put("_charset_", "utf-8");

	    JSONObject req4 = new JSONObject();
	    req4.put("url", CREATE_FILE_URI);
	    req4.put("method", "POST");
	    req4.put("parameters", wikiP);
	    req4.put("_charset_", "utf-8");
	    batchPosts.add(req4);

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    JSONObject post8Response = http(client, method);

	    // Go through the response and get the document UUID for the library and participants items.
	    String syllabusDocHash = null;
	    String contactUsDocHash = null;
	    String orgNotesDocHash = null;
	    String studentWikiDocHash = null;

	    if (post8Response != null){
	    	try {
	    		JSONArray docResults = post8Response.getJSONArray("results");
	    		@SuppressWarnings("unchecked")
				Iterator<JSONObject> itr = docResults.iterator();
	    		while(itr.hasNext()){
	    			JSONObject result = itr.next();
	    			JSONObject body = result.getJSONObject("body");
	    			JSONObject contentItem = body.getJSONObject("_contentItem");
	    			JSONObject item = contentItem.getJSONObject("item");
	    			String poolId = (String)contentItem.get("poolId");
	    			if (item.getString("sakai:pooled-content-file-name").equals("Syllabus")){
	    				syllabusDocHash = poolId;
	    			}
	    			else if (item.getString("sakai:pooled-content-file-name").equals("Organization Notes")){
	    				orgNotesDocHash = poolId;
	    			}
	    			else if (item.getString("sakai:pooled-content-file-name").equals("Contact us")){
	    				contactUsDocHash = poolId;
	    			}
	    			else if (item.getString("sakai:pooled-content-file-name").equals("Student Wiki")){
	    				studentWikiDocHash = poolId;
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

	    JSONObject syllabusParams = new JSONObject();
	    syllabusParams.put(":operation", "import");
	    syllabusParams.put(":contentType", "json");
	    syllabusParams.put(":replace", true);
	    syllabusParams.put(":replaceProperties", true);
	    syllabusParams.put("_charset_", "utf-8");
	    JSONObject syllabusContent = new JSONObject();
	    syllabusContent.put("id6573920372", JSONSerializer.toJSON(ImmutableMap.of("page", "<p>Lorem ipsum dolor sit amet</p>")));
	    syllabusContent.put("id569856425", JSONSerializer.toJSON(ImmutableMap.of("page", "<p>Week 2</p>")));
	    syllabusContent.put("id647321988", JSONSerializer.toJSON(ImmutableMap.of("page", "<p>Week 3</p>")));
	    syllabusParams.put(":content", syllabusContent);

	    JSONObject syllabusRequest = new JSONObject();
	    syllabusRequest.put("url", "/p/" + syllabusDocHash + ".resource");
	    syllabusRequest.put("method", "POST");
	    syllabusRequest.put("parameters", syllabusParams);
	    syllabusRequest.put("_charset", "utf-8");

	    JSONObject contactParams = new JSONObject();
	    contactParams.put(":operation", "import");
	    contactParams.put(":contentType", "json");
	    contactParams.put(":replace", true);
	    contactParams.put(":replaceProperties", true);
	    contactParams.put("_charset_", "utf-8");
	    JSONObject contactContent = new JSONObject();
	    contactContent.put("id6573920372", JSONSerializer.toJSON(ImmutableMap.of("page", "<p><strong>Contact Us</strong></p><p>16 Mill Lane<br>1st Floor<br>CB2 1SB Cambridge</p><p><img id='widget_googlemaps_id439704665' class='widget_inline' style='display: block; padding: 10px; margin: 4px;' src='/devwidgets/googlemaps/images/googlemaps.png' data-mce-src='/devwidgets/googlemaps/images/googlemaps.png' data-mce-style='display: block; padding: 10px; margin: 4px;' border='1'><br></p>")));
	    contactParams.put(":content", contactContent);

	    JSONObject contactRequest = new JSONObject();
	    contactRequest.put("url", "/p/" + contactUsDocHash + ".resource");
	    contactRequest.put("method", "POST");
	    contactRequest.put("parameters", contactParams);
	    contactRequest.put("_charset", "utf-8");

	    JSONObject orgNotesParams = new JSONObject();
	    orgNotesParams.put(":operation", "import");
	    orgNotesParams.put(":contentType", "json");
	    orgNotesParams.put(":replace", true);
	    orgNotesParams.put(":replaceProperties", true);
	    orgNotesParams.put("_charset_", "utf-8");
	    JSONObject orgNotesContent = new JSONObject();
	    orgNotesContent.put("id6573920372", JSONSerializer.toJSON(ImmutableMap.of("page", "<p>This is some information about the course<br></p>")));
	    orgNotesContent.put("id373710599", JSONSerializer.toJSON(ImmutableMap.of("page", "<p>This is some information for prospective students<br>")));
	    orgNotesParams.put(":content", orgNotesContent);

	    JSONObject orgNotesRequest = new JSONObject();
	    orgNotesRequest.put("url", "/p/" + orgNotesDocHash + ".resource");
	    orgNotesRequest.put("method", "POST");
	    orgNotesRequest.put("_charset", "utf-8");
	    orgNotesRequest.put("parameters", orgNotesParams);

	    JSONObject wikiParams = new JSONObject();
	    wikiParams.put(":operation", "import");
	    wikiParams.put(":contentType", "json");
	    wikiParams.put(":replace", true);
	    wikiParams.put(":replaceProperties", true);
	    wikiParams.put("_charset_", "utf-8");
	    JSONObject wikiContent = new JSONObject();
	    wikiContent.put("id849031890418", JSONSerializer.toJSON(ImmutableMap.of("page", "<p>Student wiki editable by all members of this course<br></p>")));
	    wikiParams.put(":content", wikiContent);

	    JSONObject wikiRequest = new JSONObject();
	    wikiRequest.put("url", "/p/" + studentWikiDocHash + ".resource");
	    wikiRequest.put("method", "POST");
	    wikiRequest.put("_charset", "utf-8");
	    wikiRequest.put("parameters", wikiParams);

	    batchPosts.add(syllabusRequest);
	    batchPosts.add(contactRequest);
	    batchPosts.add(orgNotesRequest);
	    batchPosts.add(wikiRequest);

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    http(client, method);

	    log.debug("Added initial content into the sakai documents.");

	    // ----------------------------------------------------------------
	    // POST 10 - Set ACLs on sakai documents.
	    batchPosts.clear();
	    JSONObject request1 = new JSONObject();
	    JSONObject params1 = new JSONObject();
	    params1.put(":viewer","everyone");
	    params1.put(":viewer@Delete","anonymous");
	    request1.put("url", "/p/" + syllabusDocHash + ".members.json");
	    request1.put("method", "POST");
	    request1.put("parameters", params1);
	    batchPosts.add(request1);

	    JSONObject request2 = new JSONObject();
	    JSONObject params2 = new JSONObject();
	    params2.put("principalId","everyone");
	    params2.put("privilege@jcr:read","granted");
	    request2.put("url", "/p/" + syllabusDocHash + ".modifyAce.json");
	    request2.put("method", "POST");
	    request2.put("parameters", params2);
	    batchPosts.add(request2);

	    JSONObject request3 = new JSONObject();
	    JSONObject params3 = new JSONObject();
	    params3.put("principalId","anonymous");
	    params3.put("privilege@jcr:read","denied");
	    request3.put("url", "/p/" + syllabusDocHash + ".modifyAce.json");
	    request3.put("method", "POST");
	    request3.put("parameters", params3);
	    batchPosts.add(request3);

	    JSONObject request4 = new JSONObject();
	    JSONObject params4 = new JSONObject();
	    params4.put(":viewer@Delete", new String[] {"everyone","anonymous"});
	    params4.put("privilege@jcr:read","denied");
	    request4.put("url", "/p/" + contactUsDocHash + ".members.json");
	    request4.put("method", "POST");
	    request4.put("parameters", params4);
	    batchPosts.add(request4);

	    JSONObject request5 = new JSONObject();
	    JSONObject params5 = new JSONObject();
	    params5.put("principalId", new String[] {"everyone","anonymous"});
	    params5.put("privilege@jcr:read","denied");
	    request5.put("url", "/p/" + contactUsDocHash + ".modifyAce.json");
	    request5.put("method", "POST");
	    request5.put("parameters", params5);
	    batchPosts.add(request5);

	    JSONObject request6 = new JSONObject();
	    JSONObject params6 = new JSONObject();
	    params6.put(":viewer", new String[] {"everyone","anonymous"});
	    request6.put("url", "/p/" + orgNotesDocHash + ".members.json");
	    request6.put("method", "POST");
	    request6.put("parameters", params6);
	    batchPosts.add(request6);

	    JSONObject request7 = new JSONObject();
	    JSONObject params7 = new JSONObject();
	    params7.put("principalId", new String[] {"everyone","anonymous"});
	    request7.put("url", "/p/" + contactUsDocHash + ".modifyAce.json");
	    request7.put("method", "POST");
	    request7.put("parameters", params7);
	    batchPosts.add(request7);

	    JSONObject request8 = new JSONObject();
	    JSONObject params8 = new JSONObject();
	    params8.put(":viewer@Delete", new String[] {"everyone","anonymous"});
	    request8.put("url", "/p/" + studentWikiDocHash + ".members.json");
	    request8.put("method", "POST");
	    request8.put("parameters", params8);
	    batchPosts.add(request8);

	    JSONObject request9 = new JSONObject();
	    JSONObject params9 = new JSONObject();
	    params9.put("principalId", new String[] {"everyone","anonymous"});
	    params9.put("privilege@jcr:read","denied");
	    request9.put("url", "/p/" + studentWikiDocHash + ".modifyAce.json");
	    request9.put("method", "POST");
	    request9.put("parameters", params9);
	    batchPosts.add(request9);

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    http(client, method);

	    log.debug("Set ACLs on sakai documents.");


	    // -------------------------------------------------------------
	    // POST 11 - Add the groups as managers of the doc.
	    batchPosts.clear();
	    for (String docHash : new String[]{ syllabusDocHash, contactUsDocHash, orgNotesDocHash, studentWikiDocHash}){
	    	for (String roleGroupId: new String[]{ studentGroupId, taGroupId, lecturerGroupId}){
	    		JSONObject request = new JSONObject();
	    		request.put("url", "/p/" + docHash + ".members.html");
	    		request.put("_charset_", "utf-8");
	    		request.put("parameters", ImmutableMap.of(":manager", roleGroupId, "_charset_", "utf-8"));
	    		batchPosts.add(request);
	    	}
	    }

	    method = new PostMethod(url + BATCH_URI);
	    json = JSONArray.fromObject(batchPosts);
	    method.setParameter(BATCH_REQUESTS_PARAM, json.toString());
	    http(client, method);

	    method = new PostMethod(url + "/~" + parentGroupId + ".docstructure");

	    method.setParameter(":operation", "import");
	    method.setParameter(":contentType", "json");
	    method.setParameter(":replace", "true");
	    method.setParameter(":replaceProperties", "true");
	    method.setParameter("_charset_", "utf-8");
	    JSONObject content = new JSONObject();
	    String structure = "{\"syllabus\":{\"_title\":\"Syllabus\",\"_order\":0,\"_view\":\"[\\\"everyone\\\",\\\"-student\\\",\\\"-ta\\\"]\",\"_edit\":\"[\\\"-lecturer\\\"]\",\"_pid\":\"#{SYLLABUS_DOC_HASH}\"},\"contactus\":{\"_title\":\"Contact us\",\"_order\":1,\"_view\":\"[\\\"-student\\\"]\",\"_edit\":\"[\\\"-lecturer\\\",\\\"-ta\\\"]\",\"_pid\":\"#{CONTACTUS_DOC_HASH}\"},\"organizationnotes\":{\"_title\":\"Organization Notes\",\"_order\":3,\"_view\":\"[\\\"-ta\\\"]\",\"_edit\":\"[\\\"-lecturer\\\"]\",\"_pid\":\"#{ORGNOTES_DOC_HASH}\"},\"studentwiki\":{\"_title\":\"Student Wiki\",\"_order\":4,\"_view\":\"[]\",\"_edit\":\"[\\\"-lecturer\\\",\\\"-ta\\\",\\\"-student\\\"]\",\"_pid\":\"#{STUDENTWIKI_DOC_HASH}\"}}";
	    structure = structure.replaceAll("#\\{SYLLABUS_DOC_HASH\\}", syllabusDocHash);
	    structure = structure.replaceAll("#\\{CONTACTUS_DOC_HASH\\}", contactUsDocHash);
	    structure = structure.replaceAll("#\\{SYLLABUS_DOC_HASH\\}", syllabusDocHash);
	    structure = structure.replaceAll("#\\{STUDENTWIKI_DOC_HASH\\}", studentWikiDocHash);
	    content.put("structure0", structure);
	    method.setParameter("content", content.toString());
	    http(client, method);

	    log.info("Imported docstructure.");
	}

	@Override
	public void deleteGroup(String groupId, String groupName)
			throws GroupModificationException {
		String nakamuraGroupId = groupIdAdapter.getNakamuraGroupId(groupName);

		if (nakamuraGroupId.endsWith(SimpleGroupEsbConsumer.MEMBER_SUFFIX)){
			String parentGroupId = getPseudoGroupParent(nakamuraGroupId);
			String lecturerGroupId = parentGroupId + "-lecturer";
			String taGroupId = parentGroupId + "-ta";
			String studentGroupId = parentGroupId + "-student";

			for (String deleteId: new String[] { lecturerGroupId, taGroupId, studentGroupId, parentGroupId }){
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

	@Override
	public void addMembership(String groupId, String groupName, String memberId)
	throws GroupModificationException {
		addMembership(groupIdAdapter.getNakamuraGroupId(groupName), memberId);
	}

	@Override
	public void deleteMembership(String groupId, String groupName, String memberId)
	throws GroupModificationException {
		deleteMembership(groupIdAdapter.getNakamuraGroupId(groupName), memberId);
	}

	public void setGroupIdAdapter(TemplateGroupIdAdapter adapter){
		this.groupIdAdapter = adapter;
	}
}
