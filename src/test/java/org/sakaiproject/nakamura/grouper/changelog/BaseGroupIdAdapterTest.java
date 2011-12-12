package org.sakaiproject.nakamura.grouper.changelog;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class BaseGroupIdAdapterTest extends TestCase {

	private BaseGroupIdAdapter gAdapter;

	@Override
	public void setUp(){
		gAdapter = new SimpleGroupIdAdapter();
		gAdapter.setPseudoGroupSuffixes(ImmutableSet.of("manager", "member", "student", "lecturer", "ta"));
	}

	public void testGetPseudoGroupParent(){
		assertEquals("some-group", gAdapter.getWorldId("some-group"));
		assertEquals("some-group", gAdapter.getWorldId("some-group-student"));

		assertEquals("some_group", gAdapter.getWorldId("some_group"));
		assertEquals("some_group", gAdapter.getWorldId("some_group-student"));
		assertEquals("some_group_student", gAdapter.getWorldId("some_group_student"));
	}
}
