package edu.nyu.grouper.xmpp;

import java.util.HashMap;

import edu.internet2.middleware.grouperClient.util.GrouperClientUtils;
import edu.internet2.middleware.grouperClientExt.org.apache.commons.httpclient.methods.PostMethod;
import edu.nyu.grouper.xmpp.api.InitialGroupPropertiesProvider;

/**
 * Read the list of initial group properties from the grouperClient.properties file.
 * This is a temporary approach until we can pull the desired group properties from sakai3.
 * @author Erik Froese
 */
public class ConfigFileInitialGroupPropertiesProvider implements InitialGroupPropertiesProvider {
	
	// Prefix for properties in grouperClient.properties
	private static String PROPERTIES_PREFIX = "grouperClient.xmpp.nakamura.group";

	private HashMap<String, String> initialProperties;
	
	public ConfigFileInitialGroupPropertiesProvider(){
		loadPropertiesFromFile();
	}

	public void addProperties(String groupId, String groupExtension,
			PostMethod method) {
		for(String key: initialProperties.keySet()){
			method.addParameter(key, initialProperties.get(key));
		}
	}
	
	private void loadPropertiesFromFile(){
		initialProperties = new HashMap<String, String>();
		String groupJoinable = GrouperClientUtils.propertiesValue(PROPERTIES_PREFIX + ".group-joinable", true); 
		String groupVisable = GrouperClientUtils.propertiesValue(PROPERTIES_PREFIX + ".group-visible", true);
		String pagesVisable = GrouperClientUtils.propertiesValue(PROPERTIES_PREFIX + ".pages-visible", true);
		initialProperties.put("sakai:group-joinable", groupJoinable);	
		initialProperties.put("sakai:group-visible", groupVisable);
		initialProperties.put("sakai:pages-visible", pagesVisable);
	}
}