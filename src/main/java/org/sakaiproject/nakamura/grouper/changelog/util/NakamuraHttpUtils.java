package org.sakaiproject.nakamura.grouper.changelog.util;

import java.net.URL;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupAlreadyExistsException;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;

public class NakamuraHttpUtils {

	public static Log log = LogFactory.getLog(NakamuraHttpUtils.class);

	// This could be anything but I think this is explanatory
	private static final String HTTP_REFERER = "/system/console/grouper";
	private static final String HTTP_USER_AGENT = "Nakamura Grouper Sync";

	/**
	 * Construct an {@link HttpClient} which is configured to authenticate to Nakamura.
	 * @return the configured client.
	 */
	public static HttpClient getHttpClient(URL url, String username, String password){
		HttpClient client = new HttpClient();
		HttpState state = client.getState();
		state.setCredentials(
			new AuthScope(url.getHost(), getPort(url)),
			new UsernamePasswordCredentials(username, password));
		client.getParams().setAuthenticationPreemptive(true);
		client.getParams().setParameter("http.useragent", HTTP_USER_AGENT);
		client.getParams().setParameter("_charset_", "utf-8");
		return client;
	}

	/**
	 * If you don't specify a port when creating a {@link URL} {@link URL#getPort()} will return -1.
	 * This function uses the default HTTP/s ports
	 * @return the port for this.url. 80 or 433 if not specified.
	 */
	public static int getPort(URL url){
		int port = url.getPort();
		if (port == -1){
			if (url.getProtocol().equals("http")){
				port = 80;
			}
			else if(url.getProtocol().equals("https")){
				port = 443;
			}
		}
		return port;
	}

	/**
	 * Prepare an HTTP request to Sakai OAE and parse the response (if JSON).
	 * @param client an {@link HttpClient} to execute the request.
	 * @param method an HTTP method to send
	 * @return a JSONObject of the response if the request returns JSON
	 * @throws GroupModificationException if there was an error updating the group information.
	 */
	public static JSONObject http(HttpClient client, PostMethod method) throws GroupModificationException {

		method.setRequestHeader("User-Agent", HTTP_USER_AGENT);
		method.setRequestHeader("Referer", HTTP_REFERER);

		String errorMessage = null;
		String responseString = null;
		JSONObject responseJSON = null;

		boolean isJSONRequest = ! method.getPath().toString().endsWith(".html");

		if (log.isDebugEnabled()){
			log.debug(method.getName() + " " + method.getPath() + " params:");
			for (NameValuePair nvp : method.getParameters()){
				log.debug(nvp.getName() + " = " + nvp.getValue());
			}
		}

		int responseCode = -1;
		try{
			responseCode = client.executeMethod(method);
			responseString = StringUtils.trimToNull(IOUtils.toString(method.getResponseBodyAsStream()));

			if(isJSONRequest){
				responseJSON = parseJSONResponse(responseString);
			}

			if(log.isDebugEnabled()){
				log.debug(responseCode + " " + method.getName() + " " + method.getPath());
			}
			if (log.isTraceEnabled()){
				log.trace("reponse: " + responseString);
			}

			switch (responseCode){

			case HttpStatus.SC_OK: // 200
			case HttpStatus.SC_CREATED: // 201
				break;
			case HttpStatus.SC_BAD_REQUEST: // 400
			case HttpStatus.SC_UNAUTHORIZED: // 401
			case HttpStatus.SC_NOT_FOUND: // 404
			case HttpStatus.SC_INTERNAL_SERVER_ERROR: // 500
				if (isJSONRequest && responseJSON != null){
					errorMessage = responseJSON.getString("status.message");
				}
				if (errorMessage == null){
					errorMessage = "Empty "+ responseCode + " error. Check the logs on the Sakai OAE server.";
				}
				break;
			default:
				errorMessage = "Unknown HTTP response " + responseCode;
				break;
			}
		}
		catch (Exception e) {
			errorMessage = "An exception occurred communicatingSakai OAE. " + e.toString();
		}
		finally {
			method.releaseConnection();
		}

		if (errorMessage != null){
			log.error(errorMessage);
			errorToException(responseCode, errorMessage);
		}
		return responseJSON;
	}

	/**
	 * Try to parse the HTTP response as JSON.
	 * @param response the HTTP response body as a String.
	 * @return a JSONObject representing the parsed response. null if not JSON or null.
	 */
	protected static JSONObject parseJSONResponse(String response){
		JSONObject json = null;
		if (response != null){
			try {
				json = JSONObject.fromObject(response);
			}
			catch (JSONException je){
				if (response.startsWith("<html>")){
					log.debug("Expected a JSON response, got html");
				}
				else {
					log.error("Could not parse JSON response. " + response);
				}
			}
		}
		return json;
	}

	/**
	 * Throw a specific exception given a JSON response from a Sakai OAE server.
	 * @param response
	 * @throws GroupModificationException
	 * @throws GroupAlreadyExistsException
	 */
	protected static void errorToException(int code, String errorMessage) throws GroupModificationException, GroupAlreadyExistsException {
		if (errorMessage == null){
			return;
		}
		// TODO: If this is a constant somewhere include the nakamura jar in the
		// lib directory and use the constant.
		if (errorMessage.startsWith("A principal already exists with the requested name")){
			throw new GroupAlreadyExistsException(errorMessage);
		}

		throw new GroupModificationException(code, errorMessage);
	}
}
