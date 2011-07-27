package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.util.api.GroupIdAdapter;

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
public class TemplateGroupIdAdapter implements GroupIdAdapter {

	private static Log log = LogFactory.getLog(TemplateGroupIdAdapter.class);

	private Pattern pattern;
	private String nakamuraIdTemplate;
	private Set<String> pseudoGroupSuffixes;
	private Set<String> includeExcludeSuffixes;

	public String getNakamuraGroupId(String grouperName) {

		if (grouperName == null){
			return null;
		}

		String nakamuraGroupId = getIdForCourseGroup(grouperName);

		// If the groupername ends in _SUFFIX we change that to -SUFFIX
		for (String suffix: pseudoGroupSuffixes){
			if (nakamuraGroupId.endsWith("_" + suffix)){
				nakamuraGroupId = nakamuraGroupId.substring(0, nakamuraGroupId.lastIndexOf("_")) + "-" + suffix;
				break;
			}
			// If the groupername ends in _SUFFIX_systemOfRecord we change that to -SUFFIX
			for (String ieSuffix: includeExcludeSuffixes) {
				if (nakamuraGroupId.endsWith("_" + suffix + ieSuffix)){
					nakamuraGroupId = nakamuraGroupId.substring(0, nakamuraGroupId.lastIndexOf("_"));
					int newlast = nakamuraGroupId.lastIndexOf("_");
					nakamuraGroupId = nakamuraGroupId.substring(0,newlast) + "-" + nakamuraGroupId.substring(newlast + 1);
					break;
				}
			}
		}

		log.debug(grouperName + " => " + nakamuraGroupId);
		return nakamuraGroupId;
	}

	private String getIdForCourseGroup(String grouperName){
		Matcher matcher = pattern.matcher(grouperName);

		if (!matcher.find()){
			throw new RuntimeException(grouperName + " does not match the regex in ");
		}
		String[] g = new String[matcher.groupCount()];
		for (int i = 1; i <= matcher.groupCount(); i++){
			g[i-1] = matcher.group(i);
		}
		JexlEngine jexl = new JexlEngine();
		Expression e = jexl.createExpression(nakamuraIdTemplate);
		JexlContext jc = new MapContext();
		jc.set("g", g);

		String nakamuraGroupId = (String)e.evaluate(jc);
		if (nakamuraGroupId == null){
			return null;
		}
		return nakamuraGroupId;
	}

	public void setPattern(Pattern pattern) {
		this.pattern = pattern;
	}

	public void setNakamuraIdTemplate(String nakamuraIdTemplate) {
		this.nakamuraIdTemplate = nakamuraIdTemplate;
	}

	public void setPseudoGroupSuffixes(Set<String> pseudoGroupSuffixes) {
		this.pseudoGroupSuffixes = pseudoGroupSuffixes;
	}

	public void setIncludeExcludeSuffixes(Set<String> includeExcludeSuffixes) {
		this.includeExcludeSuffixes = includeExcludeSuffixes;
	}

}
