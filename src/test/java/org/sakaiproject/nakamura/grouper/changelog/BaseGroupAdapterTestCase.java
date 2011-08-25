package org.sakaiproject.nakamura.grouper.changelog;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;
import static org.powermock.api.support.membermodification.MemberMatcher.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sakaiproject.nakamura.grouper.changelog.exceptions.GroupModificationException;
import org.sakaiproject.nakamura.grouper.changelog.util.NakamuraHttpUtils;

import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.SubjectNotFoundException;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { GrouperUtil.class, GroupFinder.class,
		GrouperSession.class, SubjectFinder.class,
		NakamuraHttpUtils.class})
public class BaseGroupAdapterTestCase extends TestCase {

	private HttpSimpleGroupAdapter groupAdapter;

	private HttpClient httpClient;

	private Subject user1;

	private String userId = "user1";

	private URL url;

	@Before
	public void setUp() throws MalformedURLException{
		// Get rids of this troublesome method
		suppress(method(GrouperUtil.class, "getLog"));

		url = new URL("http://localhost:8080");
		// Our mocks
		httpClient = mock(HttpClient.class);
		user1 = mock(Subject.class);
		mockStatic(NakamuraHttpUtils.class);
		mockStatic(SubjectFinder.class);

		// Universal actions
		when(NakamuraHttpUtils.getHttpClient(any(URL.class), anyString(), anyString()))
				.thenReturn(httpClient);

		groupAdapter = new HttpSimpleGroupAdapter();
		groupAdapter.createUsers = true;
		groupAdapter.setUrl(url);
		groupAdapter.emailAttribute = "email";
		groupAdapter.firstNameAttribute = "givenName";
		groupAdapter.lastNameAttribute = "sn";
	}

	@Test
	public void testUserAlreadyExsists() throws HttpException, IOException, GroupModificationException{
		when(httpClient.executeMethod(any(HttpMethod.class))).thenReturn(200);
		groupAdapter.createOAEUser(userId);
		verify(httpClient).executeMethod(any(GetMethod.class));
		verifyNoMoreInteractions(httpClient);
	}

	@Test(expected=GroupModificationException.class)
	public void testUserDoesntExsistInSakaiOrGrouper() throws HttpException, IOException, GroupModificationException{
		when(httpClient.executeMethod(any(HttpMethod.class))).thenReturn(404);
		when(SubjectFinder.findByIdOrIdentifier(userId, true)).thenThrow(new SubjectNotFoundException("user1"));
		groupAdapter.createOAEUser(userId);
		verify(httpClient, times(1)).executeMethod(any(HttpMethod.class));
	}

	public void testUserDoesntExsistInSakai() throws Exception{
		when(httpClient.executeMethod(any(HttpMethod.class))).thenReturn(404);

		when(user1.getAttributeValue("givenName")).thenReturn("FIRST");
		when(user1.getAttributeValue("sn")).thenReturn("LAST");
		when(user1.getAttributeValue("email")).thenReturn("EMAIL");

		when(SubjectFinder.findByIdOrIdentifier(userId, true)).thenReturn(user1);
		whenNew(PostMethod.class).withArguments(url.toString() + BaseGroupAdapter.USER_CREATE_URI).thenReturn(mock(PostMethod.class));
		groupAdapter.createOAEUser(userId);
		verify(httpClient, times(1)).executeMethod(any(HttpMethod.class));

		verify(user1).getAttributeValue("givenName");
		verify(user1).getAttributeValue("sn");
		verify(user1).getAttributeValue("email");
	}

	public void testDryRun() throws Exception{
		when(httpClient.executeMethod(any(HttpMethod.class))).thenReturn(404);

		when(user1.getAttributeValue("givenName")).thenReturn("FIRST");
		when(user1.getAttributeValue("sn")).thenReturn("LAST");
		when(user1.getAttributeValue("email")).thenReturn("EMAIL");

		when(SubjectFinder.findByIdOrIdentifier(userId, true)).thenReturn(user1);
		whenNew(PostMethod.class).withArguments(url.toString() + BaseGroupAdapter.USER_CREATE_URI).thenReturn(mock(PostMethod.class));
		groupAdapter.dryrun = true;
		groupAdapter.createOAEUser(userId);
		verifyNoMoreInteractions(httpClient);
	}
}