package org.sakaiproject.nakamura.grouper.changelog;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableSet;

public class BaseGroupAdapterTest extends TestCase {

	private BaseGroupIdAdapter gAdapter;

	@Override
	public void setUp(){
		gAdapter = new SimpleGroupIdAdapter();
		gAdapter.setPseudoGroupSuffixes(ImmutableSet.of("manager", "member", "student", "lecturer", "ta"));
	}

	public void testGetPseudoGroupParent(){
		assertEquals("some-group", gAdapter.getPseudoGroupParent("some-group"));
		assertEquals("some-group", gAdapter.getPseudoGroupParent("some-group-student"));

		assertEquals("some_group", gAdapter.getPseudoGroupParent("some_group"));
		assertEquals("some_group", gAdapter.getPseudoGroupParent("some_group-student"));
		assertEquals("some_group_student", gAdapter.getPseudoGroupParent("some_group_student"));
	}
}
