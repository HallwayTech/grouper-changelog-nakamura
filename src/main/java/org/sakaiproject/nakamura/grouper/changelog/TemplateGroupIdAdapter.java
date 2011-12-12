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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdAdapter;

import com.google.common.collect.ImmutableMap;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;

/**
 * A flexible way to map grouper names to SakaiOAE groups.
 *
 * Capture parts of the grouper name and use them in a template to create the
 * authorizableId of the corresponding group in SakaiOAE. Paramaters captured
 * in the regular expression {@link Pattern} are available as an array named g.
 *
 * This implementation uses the Jexl2 expression language because its pretty
 * simple and available already on the grouper loader classpath.
 *
 * Configure this component in the grouper-loader.properties
 * nakamura.groupname.regex = "pre:fix:([^:]+):([^:]+):([^:]+)"
 * nakamura.nakamuraid.template = "'sakai_' + g[0] + '_' + g[1] + '_' + g[2]"
 */
public class TemplateGroupIdAdapter extends BaseGroupIdAdapter implements GroupIdAdapter {

	private static Log log = LogFactory.getLog(TemplateGroupIdAdapter.class);

	// Used to parse the grouper name of a provisioned course
	protected Pattern pattern;
	// Used to create an id for Sakai OAE
	protected String nakamuraIdTemplate;

	// Configuration keys
	public static final String PROP_REGEX = "TemplateGroupIdAdapter.groupName.regex";
	public static final String PROP_NAKID_TEMPLATE = "TemplateGroupIdAdapter.groupId.template";

	/**
	 * Load the configuration from the grouper-loader.properties
	 */
	public void loadConfiguration(String consumerName) {
		super.loadConfiguration(consumerName);
		String cfgPrefix = "changeLog.consumer." + consumerName + ".";
		pattern = Pattern.compile(GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_REGEX, true));
		nakamuraIdTemplate = GrouperLoaderConfig.getPropertyString(cfgPrefix + PROP_NAKID_TEMPLATE, true);
	}

	@Override
	public String getGroupId(String grouperName) {

		if (grouperName == null){
			return null;
		}

		String nakamuraGroupId;
		try {
			nakamuraGroupId = applyTemplate(grouperName);
		} catch (Exception e) {
			log.error(e);
			return null;
		}
		String originalExtension = StringUtils.substringAfterLast(grouperName, ":");

		// If the groupername ends in one of the include/exclude groups we remove that suffix.
		for (String ieSuffix: includeExcludeSuffixes) {
			if (nakamuraGroupId.endsWith(ieSuffix)){
				nakamuraGroupId = StringUtils.removeEnd(nakamuraGroupId, ieSuffix);
				originalExtension = StringUtils.removeEnd(originalExtension, ieSuffix);
				break;
			}
		}

		// Re-write the extension via the role map
		String role = originalExtension;
		if (roleMap.containsKey(role)){
			role = roleMap.get(role);
		}
		if (pseudoGroupSuffixes.contains(role)){
			nakamuraGroupId = StringUtils.removeEnd(nakamuraGroupId, "_" + originalExtension);
			nakamuraGroupId += "-" + role;
		}

		log.debug(grouperName + " => " + nakamuraGroupId);
		return nakamuraGroupId;
	}

	/**
	 * Create the course Id for OAE from the grouperName using a template.
	 * @param grouperName the name of a group in Grouper
	 * @return the template filled in with matches from the pattern
	 * @throws Exception
	 */
	private String applyTemplate(String grouperName) throws Exception{
		Matcher matcher = pattern.matcher(grouperName);

		if (!matcher.find()){
			throw new Exception(grouperName + " does not match the regex in ");
		}

		List<String> g = new ArrayList<String>();
		for (int i = 1; i <= matcher.groupCount(); i++){
			g.add(matcher.group(i));
		}

		Expression e = new JexlEngine().createExpression(nakamuraIdTemplate);
		JexlContext jc = new MapContext(ImmutableMap.of("g", (Object)g));
		String nakamuraGroupId = (String)e.evaluate(jc);

		if (nakamuraGroupId == null){
			return null;
		}
		return nakamuraGroupId;
	}
}
