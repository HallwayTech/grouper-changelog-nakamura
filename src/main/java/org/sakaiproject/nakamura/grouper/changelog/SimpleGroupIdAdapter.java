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

import org.apache.commons.lang.StringUtils;
import org.sakaiproject.nakamura.grouper.changelog.api.GroupIdAdapter;

public class SimpleGroupIdAdapter extends AbstractGroupIdAdapter implements GroupIdAdapter {

	@Override
	public String getGroupId(String grouperName) {
		String nakamuraGroupId = grouperName.replaceAll(":", "_");

		if (nakamuraGroupId.startsWith("_")){
			nakamuraGroupId = nakamuraGroupId.substring(1);
		}

		// If the groupername ends in _SUFFIX we change that to -SUFFIX
		for (String psSuffix: pseudoGroupSuffixes){
			String nakamuraSuffix = roleMap.get(psSuffix);
			if (nakamuraSuffix == null){
				nakamuraSuffix = psSuffix;
			}
			if (nakamuraGroupId.endsWith("_" + psSuffix)){
				nakamuraGroupId = StringUtils.substringBeforeLast(nakamuraGroupId, "_") + "-" + psSuffix;
				break;
			}
			// If the grouperName ends in _SUFFIX_systemOfRecord we change that to -SUFFIX
			for (String ieSuffix: includeExcludeSuffixes) {
				if (nakamuraGroupId.endsWith("_" + psSuffix + ieSuffix)){
					// nakamuraGroupId = some_group_SUFFIX
					nakamuraGroupId = StringUtils.substringBeforeLast(nakamuraGroupId, "_");
					// suffix = SUFFIX
					String suffix = StringUtils.substringAfterLast(nakamuraGroupId, "_");
					// nakamuraGroupId = some_group-SUFFIX
					nakamuraGroupId = StringUtils.substringBeforeLast(nakamuraGroupId, "_") + "-" + suffix;
					break;
				}
			}
		}

		return nakamuraGroupId;
	}
}