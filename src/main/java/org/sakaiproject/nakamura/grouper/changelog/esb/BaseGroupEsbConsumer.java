package org.sakaiproject.nakamura.grouper.changelog.esb;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.BaseGroupIdAdapter;
import org.sakaiproject.nakamura.grouper.changelog.GroupIdAdapterImpl;
import org.sakaiproject.nakamura.grouper.changelog.api.NakamuraManager;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.UserModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.ChangeLogUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GroupType;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.Stem.Scope;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.exception.GrouperException;
import edu.internet2.middleware.grouper.exception.SessionException;
import edu.internet2.middleware.grouper.misc.SaveMode;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.provider.SubjectTypeEnum;

/**
 * Common data and methods for the other EsbConsumers.
 */
public abstract class BaseGroupEsbConsumer extends ChangeLogConsumerBase {

	private static Log log = LogFactory.getLog(BaseGroupEsbConsumer.class);

	public static final String CONFIG_PREFIX = "changeLog.consumer";

	// See the README.md for details about the configuration.
	public static final String PROP_URL = "url";
	protected URL url;

	public static final String PROP_USERNAME = "username";
	protected String username;

	public static final String PROP_PASSWORD = "password";
	protected String password;

	public static final String PROP_DRYRUN = "dryrun";
	public static final boolean DEFAULT_DRYRUN = false;
	protected boolean dryrun = DEFAULT_DRYRUN;

	public static final String PROP_CREATE_USERS = "create.users";
	public static final boolean DEFAULT_CREATE_USERS = false;
	protected boolean createUsers = DEFAULT_CREATE_USERS;

	public static final String PROP_LDAP_ATTRIBUTE_FIRST_NAME = "firstname.attribute";
	protected String firstNameAttribute;

	public static final String PROP_LDAP_ATTRIBUTE_LAST_NAME = "lastname.attribute";
	protected String lastNameAttribute;

	public static final String PROP_LDAP_ATTRIBUTE_EMAIL = "email.attribute";
	protected String emailAttribute;

	public static final String PROP_DEFAULT_EMAIL_DOMAIN = "email.domain";
	protected String defaultEmailDomain;

	public static final String PROP_ALLOW_INSTITUTIONAL = "allow.institutional";
	public static final boolean DEFAULT_ALLOW_INSTITUTIONAL = false;
	protected boolean allowInstitutional = DEFAULT_ALLOW_INSTITUTIONAL;

	public static final String PROP_PSEUDOGROUP_SUFFIXES = "pseudoGroup.suffixes";

	public static final String PROP_DELETE_ROLE = "trigger.role";
	public static final String DEFAULT_DELETE_ROLE = "students";
	protected String triggerRole = DEFAULT_DELETE_ROLE;

	public static final String PROP_ADD_ADMIN_AS = "add.admin.as";
	protected String addAdminAs = "";
	protected static final String ADMIN_USERNAME = "admin";

	public static final String PROP_GROUP_TYPE_NAME_TRIGGER = "group.type.name.trigger";
	public static final String DEFAULT_GROUP_TYPE_NAME_TRIGGER= "provisionToOAE";
	protected String groupTypeNameTrigger;

	protected boolean configurationLoaded = false;

	// Suffixes for the composite groups created for addIncludeExclude groups
	protected Set<String> includeExcludeSuffixes;

	// Sakai OAE pseudoGroup suffixes.
	protected Set<String> pseudoGroupSuffixes;

	// Authenticated session for the Grouper API
	protected GrouperSession grouperSession;

	protected NakamuraManager nakamuraManager;
	protected GroupIdAdapterImpl groupIdAdapter;


	public static final String ADD_INCLUDE_EXCLUDE = "addIncludeExclude";

	/**
	 * Load settings from grouper-loader.properties
	 * @param consumerName the name of this consumer job.
	 */
	protected void loadConfiguration(String consumerName){

		if (configurationLoaded){
			return;
		}

		String cfgPrefix = CONFIG_PREFIX + "." + consumerName + ".";
		try {
			url = new URL(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_URL, true));
		} catch (MalformedURLException e) {
			String msg = "Unable to parse config. " +  cfgPrefix + PROP_URL;
			log.error(msg);
			throw new RuntimeException(msg);
		}
		log.info("Sakai OAE url = " + url);
		username = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_USERNAME, true);
		log.info("Sakai OAE username = " + username);
		password = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PASSWORD, true);
		log.info("Sakai OAE password = XXXXXXXXX");
		dryrun = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_DRYRUN, DEFAULT_DRYRUN);
		log.info("dryrun = " + dryrun);
		triggerRole = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_DELETE_ROLE, DEFAULT_DELETE_ROLE);
		log.info("triggerRole = " + triggerRole);
		addAdminAs = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_ADD_ADMIN_AS, "");
		log.info("addAdminAs = " + addAdminAs);

		groupTypeNameTrigger = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_GROUP_TYPE_NAME_TRIGGER, DEFAULT_GROUP_TYPE_NAME_TRIGGER);
		log.info("groupTypeNameTrigger = " + groupTypeNameTrigger);

		// Create the group type if it doesn't exist
		GroupType.createType(getGrouperSession(), groupTypeNameTrigger, false);

		allowInstitutional = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_ALLOW_INSTITUTIONAL, DEFAULT_ALLOW_INSTITUTIONAL);
		log.info("allowInstitutional = " + allowInstitutional);
		createUsers = GrouperLoaderConfig.getPropertyBoolean(cfgPrefix + PROP_CREATE_USERS, DEFAULT_CREATE_USERS);
		log.info("createUsers = " + createUsers);
		firstNameAttribute = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_LDAP_ATTRIBUTE_FIRST_NAME, false);
		log.info("firstNameAttribute = " + firstNameAttribute);
		lastNameAttribute = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_LDAP_ATTRIBUTE_LAST_NAME, false);
		log.info("lastNameAttribute = " + lastNameAttribute);
		emailAttribute = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_LDAP_ATTRIBUTE_EMAIL, false);
		log.info("emailAttribute = " + emailAttribute);
		defaultEmailDomain = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_DEFAULT_EMAIL_DOMAIN, "example.edu");
		log.info("defaultEmailDomain = " + defaultEmailDomain);

		setPseudoGroupSuffixes(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PSEUDOGROUP_SUFFIXES, true));
		log.info("pseudoGroupSuffixes = " + StringUtils.join(pseudoGroupSuffixes.iterator(), ","));

		configurationLoaded = true;
	}

	/**
	 * Process a changelog entry
	 * @param entry the changelog entry to process
	 * @throws IllegalStateException
	 * @throws GroupModificationException if there's an error saving group data to Sakai OAE
	 * @throws UserModificationException if there's an error creating a user in Sakai OAE
	 */
	protected void processChangeLogEntry(ChangeLogEntry entry) throws IllegalStateException, GroupModificationException, UserModificationException {

		String grouperName = ChangeLogUtils.getGrouperNameFromChangelogEntry(entry);
		String nakamuraGroupId = groupIdAdapter.getGroupId(grouperName);
		String parentGroupId = groupIdAdapter.getPseudoGroupParent(nakamuraGroupId);

		if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_ADD)) {
			processGroupAdd(grouperName, nakamuraGroupId, parentGroupId);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_DELETE)) {
			processGroupDelete(grouperName, parentGroupId);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {
			String subjectId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
			processMembershipAdd(grouperName, nakamuraGroupId, subjectId);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE)) {
			String subjectId = entry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_DELETE.subjectId);
			processMembershipDelete(grouperName, nakamuraGroupId, subjectId);
		}
		else if (entry.equalsCategoryAndAction(ChangeLogTypeBuiltin.GROUP_TYPE_ASSIGN)) {
			String groupTypeName = entry.retrieveValueForLabel(ChangeLogLabels.GROUP_TYPE_ASSIGN.typeName);
			processGroupTypeAssign(grouperName, nakamuraGroupId, parentGroupId, groupTypeName);
		}
	}

	private void processGroupAdd(String grouperName, String nakamuraGroupId,
			String parentGroupId) throws GroupModificationException, UserModificationException {

		log.info("START GROUP_ADD : " + grouperName);
		Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);

		if (group != null) {
			// Do this even if the group already exists in Sakai OAE
			handleAllRollUpGroup(grouperName);

			if (!nakamuraManager.groupExists(parentGroupId)){
				log.info("CREATE " + parentGroupId + " as parent of " + nakamuraGroupId);

				// Special handling for inst:sis courses.
				// This will provision a group in Sakai OAE when a group is created in the institutional
				// When the group is modified in Sakai OAE it will be written back to Grouper in the
				// Sakai OAE provisioned stem.
				if (groupIdAdapter.isInstitutional(grouperName)) {
					grouperName = groupIdAdapter.toProvisioned(grouperName);
				}

				// Try to get the course description from the parent stem.
				// If the parent stem has no description just use the group id.
				String description = group.getParentStem().getDescription();
				if (description == null){
					description = parentGroupId;
				}
				nakamuraManager.createGroup(grouperName, description);

				if (StringUtils.trimToNull(addAdminAs) != null){
					nakamuraManager.addMembership(parentGroupId + "-" + addAdminAs, ADMIN_USERNAME);
				}
			}

		} else {
			log.error("Group added event received for a group that doesn't exist? " + grouperName);
		}
		log.info("DONE GROUP_ADD : " + grouperName);
	}

	/**
	 * Create the app:course:all group.
	 * @param grouperName
	 */
	private void handleAllRollUpGroup(String grouperName){
		String applicationAllGroupName = groupIdAdapter.getAllGroup(grouperName);
		if (groupIdAdapter.isInstitutional(grouperName)){
			applicationAllGroupName = groupIdAdapter.toProvisioned(applicationAllGroupName);
		}
		// Create app:sakaioae:provisioned:course:X:all if it doesn't exist
		Group applicationAllGroup = GroupFinder.findByName(getGrouperSession(), applicationAllGroupName, false);
		if (applicationAllGroup == null){
			applicationAllGroup = Group.saveGroup(getGrouperSession(), null, null,
					applicationAllGroupName, BaseGroupIdAdapter.ALL_GROUP_EXTENSION,
					null, SaveMode.INSERT, true);
			log.debug("Created " + applicationAllGroupName);
		}
		// Add the inst:sis:course:X:ROLE as a member of app:sakaoae:provisioned:course:X:all
		Subject institutionalRoleGroupSubject = SubjectFinder.findByIdOrIdentifier(grouperName, false);
		applicationAllGroup.addMember(institutionalRoleGroupSubject, false);
		log.debug("Added " + institutionalRoleGroupSubject.getName() + " as a member of " + applicationAllGroupName);
	}

	private void processGroupDelete(String grouperName, String nakamuraGroupId) throws GroupModificationException {
		log.info("START GROUP_DELETE : " + grouperName);
		if (grouperName.endsWith(triggerRole)){
			if (nakamuraManager.groupExists(nakamuraGroupId)){
				nakamuraManager.deleteGroup(nakamuraGroupId, grouperName);
			}
			else {
				log.info(nakamuraGroupId + " does not exist.");
			}
		}
		log.info("DONE GROUP_DELETE : " + grouperName);
	}

	private void processMembershipAdd(String grouperName, String nakamuraGroupId, String subjectId) throws UserModificationException, GroupModificationException {

		Subject member = SubjectFinder.findByIdOrIdentifier(subjectId, false);
		log.info("START MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + subjectId);

		if (member != null && "person".equals(member.getTypeName())
				&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){

			if (createUsers){
				nakamuraManager.createUser(subjectId);
			}

			if (nakamuraManager.groupExists(nakamuraGroupId)) {
				nakamuraManager.addMembership(nakamuraGroupId, subjectId);
			}
			else {
				log.info(nakamuraGroupId + " does not exist. Cannot add membership");
			}

			// When a user is added to inst:sis:course:G:ROLE,
			// Remove them from app:atlas:provisioned:course:G:ROLE_excludes
			if (groupIdAdapter.isInstitutional(grouperName)){
				String excludesGroupName = groupIdAdapter.toProvisioned(grouperName) + BaseGroupIdAdapter.DEFAULT_EXCLUDES_SUFFIX;
				Group excludesGroup = GroupFinder.findByName(getGrouperSession(), excludesGroupName, false);
				if (excludesGroup != null && excludesGroup.hasMember(member)){
					excludesGroup.deleteMember(member);
				}
			}
		}
		else {
			log.info("Ignoring this entry : invalid subject for membership add : " + member);
		}

		log.info("END MEMBERSHIP_ADD, group: " + grouperName + " subjectId: " + subjectId);
	}

	private void processMembershipDelete(String grouperName,
			String nakamuraGroupId, String subjectId) throws GroupModificationException {
		Subject member = SubjectFinder.findByIdOrIdentifier(subjectId, false);
		log.info("START MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + subjectId);

		if (member != null
				&& "person".equals(member.getTypeName())
				&& !groupIdAdapter.isIncludeExcludeSubGroup(grouperName)){
			if (nakamuraManager.groupExists(nakamuraGroupId)) {
				nakamuraManager.deleteMembership(nakamuraGroupId, subjectId);
			}
			else {
				log.info(nakamuraGroupId + " does not exist. Cannot remove membership");
			}

			// When a user is removed from inst:sis:course:G:ROLE,
			// Remove them from app:atlas:provisioned:course:G:ROLE_includes
			if (groupIdAdapter.isInstitutional(grouperName)){
				String includesGroupName = groupIdAdapter.toProvisioned(grouperName) + BaseGroupIdAdapter.DEFAULT_INCLUDES_SUFFIX;

				Group includesGroup = GroupFinder.findByName(getGrouperSession(), includesGroupName, false);
				if (includesGroup != null && includesGroup.hasMember(member)){
					includesGroup.deleteMember(member);
				}
			}
		}
		else {
			log.info("Ignoring this entry : invalid subject for membership delete : " + member);
		}
		log.info("END MEMBERSHIP_DELETE, group: " + grouperName + " subjectId: " + subjectId);
	}

	/**
	 * Create the group in Sakai and sync all of the role groups.
	 * @param grouperName
	 * @param nakamuraGroupId
	 * @param parentGroupId
	 * @param groupTypeName
	 * @throws GroupModificationException
	 * @throws UserModificationException
	 */
	private void processGroupTypeAssign(String grouperName,
			String nakamuraGroupId, String parentGroupId, String groupTypeName)
	throws GroupModificationException, UserModificationException {

		log.info("START GROUP_TYPE_ASSIGN, group: " + grouperName + " groupTypeName: " + groupTypeName);

		String extension = StringUtils.substringAfterLast(grouperName, ":");

		if (extension.equals(triggerRole) && groupTypeName.equals(groupTypeNameTrigger)){
			Group group = GroupFinder.findByName(getGrouperSession(), grouperName, false);
			if (group != null){
				// Provision the course
				processGroupAdd(grouperName, nakamuraGroupId, parentGroupId);

				// Sync the memberships for each of the role groups
				Stem courseStem = group.getParentStem();
				for (Group child : courseStem.getChildGroups(Scope.ONE)){
					if (!child.getExtension().equals(BaseGroupIdAdapter.ALL_GROUP_EXTENSION)){
						String childGroupId = groupIdAdapter.getGroupId(child.getName());
						log.info("Syncing memberships from " + child.getName() + " to " + childGroupId);
						nakamuraManager.addMemberships(childGroupId, getMembersPersonSubjectIds(child));
					}
				}
			}
		}

		log.info("END GROUP_TYPE_ASSIGN, group: " + grouperName + " groupTypeName: " + groupTypeName);
	}

	/**
	 * @param group
	 * @return a List of subject Ids for immediate members who are persons
	 */
	private List<String> getMembersPersonSubjectIds(Group group){
		List<String> users = new ArrayList<String>();
		for (Member member : group.getMembers()){
			if (member.isImmediateMember(group)
					&& member.getSubjectType().equals(SubjectTypeEnum.PERSON)){
				users.add(member.getSubjectId());
			}
		}
		return users;
	}

	/**
	 * Lazy-load the grouperSession
	 * @return
	 */
	protected GrouperSession getGrouperSession(){
		if ( grouperSession == null) {
			try {
				grouperSession = GrouperSession.startRootSession();
				log.debug("started session: " + this.grouperSession);
			}
			catch (SessionException se) {
				throw new GrouperException("Error starting session: " + se.getMessage(), se);
			}
		}
		return grouperSession;
	}

	public void setPseudoGroupSuffixes(String psgConfig) {
		pseudoGroupSuffixes = new HashSet<String>();
		for(String suffix: StringUtils.split(psgConfig, ",")){
			pseudoGroupSuffixes.add(suffix.trim());
		}
	}
}
