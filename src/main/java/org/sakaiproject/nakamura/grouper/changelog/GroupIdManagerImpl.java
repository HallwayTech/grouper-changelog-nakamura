/* Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.grouper.changelog;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdManager;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public class GroupIdManagerImpl extends AbstractGroupIdAdapter implements GroupIdManager {

	private static Log log = LogFactory.getLog(GroupIdManagerImpl.class);

	private Set<String> stems;
	private Set<String> provisonedStems;
	private Set<String> adhocStems;

	private TemplateGroupIdAdapter templateGroupIdAdapter;
	private SimpleGroupIdAdapter simpleGroupIdAdapter;

	private String adhocSimpleGroupsStem;
	private String adhocCourseGroupsStem;

	private String provisionedSimpleGroupsStem;
	private String provisionedCourseGroupsStem;

	private String institutionalSimpleGroupsStem;
	private String institutionalCourseGroupsStem;

	public static final String PROP_ADHOC_SIMPLE_GROUPS_STEM = "adhoc.simplegroups.stem";
	public static final String PROP_ADHOC_COURSE_GROUPS_STEM = "adhoc.coursegroups.stem";

	public static final String PROP_PROVISIONED_SIMPLE_GROUPS_STEM = "provisioned.simplegroups.stem";
	public static final String PROP_PROVISIONED_COURSE_GROUPS_STEM = "provisioned.coursegroups.stem";

	public static final String PROP_INST_SIMPLE_GROUPS_STEM = "institutional.simplegroups.stem";
	public static final String PROP_INST_COURSE_GROUPS_STEM = "institutional.coursegroups.stem";

	public GroupIdManagerImpl(){
		stems = new HashSet<String>();
		provisonedStems = new HashSet<String>();
		adhocStems = new HashSet<String>();
	}

	@Override
	public String getGroupId(String grouperName) {

		if (grouperName == null){
			return null;
		}

		StringBuilder groupId = new StringBuilder();
		if (isAdhoc(grouperName)){
			for (String astem : adhocStems){
				if (grouperName.startsWith(astem)){
					groupId.append(simpleGroupIdAdapter.getGroupId(grouperName.substring(astem.length() + 1)));
					break;
				}
			}
		}
		else if (isProvisioned(grouperName) || isInstitutional(grouperName)){
			for (String pstem : provisonedStems){
				if (grouperName.startsWith(pstem) && isCourseGroup(grouperName)){
					groupId.append(templateGroupIdAdapter.getGroupId(grouperName.substring(pstem.length() + 1)));
					break;
				}

				if (grouperName.startsWith(pstem) && isSimpleGroup(grouperName)){
					groupId.append(simpleGroupIdAdapter.getGroupId(grouperName.substring(pstem.length() + 1)));
					break;
				}
			}
		}

		String id = StringUtils.trimToNull(groupId.toString());
		log.debug(grouperName + " => " + id);
		return id;

	}

	@Override
	public String getWorldType(String grouperName){
		String type = null;
		if (isCourseGroup(grouperName)){
			type = COURSE;
		}
		else if (isSimpleGroup(grouperName)){
			type = SIMPLE;
		}
		return type;
	}

	@Override
	public String getAllGroupName(String groupName) {
		return StringUtils.substringBeforeLast(groupName, ":") + ":" + ALL_GROUP_EXTENSION;
	}

	@Override
	public String getApplicationGroupName(String grouperName){
		String provName = null;

		if (grouperName == null){
			return null;
		}

		if (isProvisioned(grouperName)){
			provName = grouperName;
		}
		if (isSimpleGroup(grouperName) && isInstitutional(grouperName)){
			provName = grouperName.replace(institutionalSimpleGroupsStem, provisionedSimpleGroupsStem);
		}
		else if (isCourseGroup(grouperName) && isInstitutional(grouperName)){
			provName = grouperName.replace(institutionalCourseGroupsStem, provisionedCourseGroupsStem);
		}

		if (provName != null){
			String role = StringUtils.substringAfterLast(provName, ":");
			if (instRoleMap.containsKey(role)){
				role = instRoleMap.get(role);
			}
			provName = StringUtils.substringBeforeLast(provName, ":") + ":" + role;
		}

		return provName;
	}

	protected boolean isProvisioned(String grouperName){
		if (grouperName != null){
			return startsWith(grouperName, provisionedSimpleGroupsStem) ||
					startsWith(grouperName, provisionedCourseGroupsStem);
		}
		return false;
	}

	public boolean isInstitutional(String grouperName){
		if (grouperName != null){
			return startsWith(grouperName, institutionalSimpleGroupsStem) ||
					startsWith(grouperName, institutionalCourseGroupsStem);
		}
		return false;
	}

	protected boolean isAdhoc(String grouperName){
		if (grouperName != null){
			return startsWith(grouperName, adhocCourseGroupsStem) ||
					startsWith(grouperName, adhocSimpleGroupsStem);
		}
		return false;
	}

	protected  boolean isCourseGroup(String grouperName){
		if (grouperName != null){
			return startsWith(grouperName, adhocCourseGroupsStem) ||
					startsWith(grouperName, provisionedCourseGroupsStem) ||
					startsWith(grouperName ,institutionalCourseGroupsStem);
		}
		return false;
	}

	protected boolean isSimpleGroup(String grouperName){
		if (grouperName != null){
			return startsWith(grouperName,adhocSimpleGroupsStem) ||
					startsWith(grouperName,provisionedSimpleGroupsStem) ||
					startsWith(grouperName,institutionalSimpleGroupsStem);
		}
		return false;
	}

	/**
	 * Avoid using the static grouper loader stuff.
	 * @param consumerName
	 */
	public void loadConfiguration(String consumerName){
		super.loadConfiguration(consumerName);
		String cfgPrefix = "changeLog.consumer." + consumerName + ".";
		setAdhocSimpleGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_ADHOC_SIMPLE_GROUPS_STEM, false));
		setAdhocCourseGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_ADHOC_COURSE_GROUPS_STEM, false));
		setProvisionedSimpleGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PROVISIONED_SIMPLE_GROUPS_STEM, false));
		setProvisionedCourseGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PROVISIONED_COURSE_GROUPS_STEM, false));
		setInstitutionalSimpleGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_INST_SIMPLE_GROUPS_STEM, false));
		setInstitutionalCourseGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_INST_COURSE_GROUPS_STEM, false));
		log.info("Loaded the configuration");
		log.info(cfgPrefix + PROP_ADHOC_SIMPLE_GROUPS_STEM + " : " + adhocSimpleGroupsStem);
		log.info(cfgPrefix + PROP_ADHOC_COURSE_GROUPS_STEM + " : " + adhocCourseGroupsStem);
		log.info(cfgPrefix + PROP_PROVISIONED_SIMPLE_GROUPS_STEM + " : " + provisionedSimpleGroupsStem);
		log.info(cfgPrefix + PROP_PROVISIONED_COURSE_GROUPS_STEM + " : " + provisionedCourseGroupsStem);
		log.info(cfgPrefix + PROP_INST_SIMPLE_GROUPS_STEM + " : " + institutionalSimpleGroupsStem);
		log.info(cfgPrefix + PROP_INST_COURSE_GROUPS_STEM + " : " + institutionalCourseGroupsStem);
	}

	private boolean startsWith(String subject, String prefix){
		if (subject == null || prefix == null){
			return false;
		}
		return subject.startsWith(prefix);
	}

	// Setters are ugly.

	public void setAdhocSimpleGroupsStem(String adhocSimpleGroupsStem) {
		stems.remove(this.adhocSimpleGroupsStem);
		adhocStems.remove(this.adhocSimpleGroupsStem);
		this.adhocSimpleGroupsStem = adhocSimpleGroupsStem;
		stems.add(this.adhocSimpleGroupsStem);
		adhocStems.add(this.adhocSimpleGroupsStem);
	}

	public void setAdhocCourseGroupsStem(String adhocCourseGroupsStem) {
		stems.remove(this.adhocCourseGroupsStem);
		adhocStems.remove(this.adhocCourseGroupsStem);
		this.adhocCourseGroupsStem = adhocCourseGroupsStem;
		stems.add(this.adhocCourseGroupsStem);
		adhocStems.add(this.adhocCourseGroupsStem);
	}

	public void setProvisionedSimpleGroupsStem(String provisionedSimpleGroupsStem) {
		stems.remove(this.provisionedSimpleGroupsStem);
		provisonedStems.remove(this.provisionedSimpleGroupsStem);
		this.provisionedSimpleGroupsStem = provisionedSimpleGroupsStem;
		stems.add(this.provisionedSimpleGroupsStem);
		provisonedStems.add(this.provisionedSimpleGroupsStem);
	}

	public void setProvisionedCourseGroupsStem(String provisionedCourseGroupsStem) {
		stems.remove(this.provisionedCourseGroupsStem);
		provisonedStems.remove(this.provisionedCourseGroupsStem);
		this.provisionedCourseGroupsStem = provisionedCourseGroupsStem;
		stems.add(this.provisionedCourseGroupsStem);
		provisonedStems.add(this.provisionedCourseGroupsStem);
	}

	public void setInstitutionalSimpleGroupsStem(
			String institutionalSimpleGroupsStem) {
		stems.remove(this.institutionalSimpleGroupsStem);
		provisonedStems.remove(this.institutionalSimpleGroupsStem);
		this.institutionalSimpleGroupsStem = institutionalSimpleGroupsStem;

		stems.add(this.institutionalSimpleGroupsStem);
		provisonedStems.add(this.institutionalSimpleGroupsStem);
	}

	public void setInstitutionalCourseGroupsStem(
			String institutionalCourseGroupsStem) {
		stems.remove(this.institutionalCourseGroupsStem);
		provisonedStems.remove(this.institutionalCourseGroupsStem);
		this.institutionalCourseGroupsStem = institutionalCourseGroupsStem;
		stems.add(this.institutionalCourseGroupsStem);
		provisonedStems.add(this.institutionalCourseGroupsStem);
	}

	public void setSimpleGroupIdAdapter(SimpleGroupIdAdapter simpleAdapter) {
		this.simpleGroupIdAdapter = simpleAdapter;
	}

	public void setTemplateGroupIdAdapter(TemplateGroupIdAdapter tmplAdapter) {
		this.templateGroupIdAdapter = tmplAdapter;
	}

	public String getAdhocSimpleGroupsStem() {
		return adhocSimpleGroupsStem;
	}

	public String getAdhocCourseGroupsStem() {
		return adhocCourseGroupsStem;
	}

	public String getProvisionedSimpleGroupsStem() {
		return provisionedSimpleGroupsStem;
	}

	public String getProvisionedCourseGroupsStem() {
		return provisionedCourseGroupsStem;
	}

	public String getInstitutionalSimpleGroupsStem() {
		return institutionalSimpleGroupsStem;
	}

	public String getInstitutionalCourseGroupsStem() {
		return institutionalCourseGroupsStem;
	}
}
