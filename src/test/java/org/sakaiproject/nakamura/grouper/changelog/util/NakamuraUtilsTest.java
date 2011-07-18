package org.sakaiproject.nakamura.grouper.changelog.util;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class NakamuraUtilsTest extends TestCase {

	public void testIsCourseGroup(){
		NakamuraUtils.psuedoGroupSuffixes = ImmutableSet.of("ta", "student", "lecturer", "manager");
		assertTrue(NakamuraUtils.isCourseGroup("some:stem:group:student"));
		assertTrue(NakamuraUtils.isCourseGroup("some:stem:group:ta"));
		assertTrue(NakamuraUtils.isCourseGroup("some:stem:group:lecturer"));
		assertFalse(NakamuraUtils.isCourseGroup("some:stem:group:manager"));
		
		// Not sure what to do about this one.
		assertFalse(NakamuraUtils.isCourseGroup("some:stem:group:members"));
	}
}
