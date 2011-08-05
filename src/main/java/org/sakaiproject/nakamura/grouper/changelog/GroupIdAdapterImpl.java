package org.sakaiproject.nakamura.grouper.changelog;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdAdapter;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

public class GroupIdAdapterImpl implements GroupIdAdapter {

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

	public static final String PROP_NAKID_ROLE_MAPPINGS = "TemplateGroupIdAdapter.role.map";

	public GroupIdAdapterImpl(){
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
		else if (isProvisioned(grouperName)){
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
		return StringUtils.trimToNull(groupId.toString());
	}

	@Override
	public String getPseudoGroupParent(String nakamuraGroupId){
		return templateGroupIdAdapter.getPseudoGroupParent(nakamuraGroupId);
	}

	protected boolean isAdhoc(String grouperName){
		if (grouperName != null){
			for (String astem : adhocStems){
				if (grouperName.startsWith(astem)){
					return true;
				}
			}
		}
		return false;
	}

	protected boolean isProvisioned(String grouperName){
		if (grouperName != null){
			for (String pstem : provisonedStems){
				if (grouperName.startsWith(pstem)){
					return true;
				}
			}
		}
		return false;
	}

	protected boolean isCourseGroup(String grouperName){
		if (grouperName != null){
			for (String stem : new String[]
			    { provisionedCourseGroupsStem, adhocCourseGroupsStem, institutionalCourseGroupsStem} ){
				if (grouperName.startsWith(stem)){
					return true;
				}
			}
		}
		return false;
	}

	protected boolean isSimpleGroup(String grouperName){
		if (grouperName != null){
			for (String stem : new String[]
			    { provisionedSimpleGroupsStem, adhocSimpleGroupsStem, institutionalSimpleGroupsStem} ){
				if (grouperName.startsWith(stem)){
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Avoid using the static grouper loader stuff.
	 * @param consumerName
	 */
	public void loadConfiguration(String consumerName){
		String cfgPrefix = "changeLog.consumer." + consumerName + ".";
		setAdhocSimpleGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_ADHOC_SIMPLE_GROUPS_STEM, false));
		setAdhocCourseGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_ADHOC_COURSE_GROUPS_STEM, false));
		setProvisionedSimpleGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PROVISIONED_SIMPLE_GROUPS_STEM, false));
		setProvisionedCourseGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_PROVISIONED_SIMPLE_GROUPS_STEM, false));
		setInstitutionalSimpleGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_INST_SIMPLE_GROUPS_STEM, false));
		setInstitutionalCourseGroupsStem(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_INST_COURSE_GROUPS_STEM, false));
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
}
