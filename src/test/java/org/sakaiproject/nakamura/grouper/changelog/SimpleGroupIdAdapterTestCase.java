package org.sakaiproject.nakamura.grouper.changelog;

import java.util.Set;

import junit.framework.TestCase;

import org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer;

import com.google.common.collect.ImmutableSet;

public class SimpleGroupIdAdapterTestCase extends TestCase  {

	private Set<String> pseudoGroupSuffixes = ImmutableSet.of("member", "manager");
	private Set<String> includeExcludeSuffixes = ImmutableSet.of("_includes", "_excludes", "_systemOfRecord", "_systemOfRecordAndIncludes");
	private String PROVISIONED_STEM = "edu:apps:sakaioae:provisioned:simplegroups";
	private String ADHOC_STEM = "edu:apps:sakaioae:adhoc:simplegroups";

	SimpleGroupIdAdapter adapter;

	@Override
	public void setUp(){
		adapter = new SimpleGroupIdAdapter();
		adapter.setPseudoGroupSuffixes(pseudoGroupSuffixes);
		adapter.setIncludeExcludeSuffixes(includeExcludeSuffixes);
		adapter.setProvisionedSimpleGroupsStem(PROVISIONED_STEM);
		adapter.setAdhocSimpleGroupsStem(ADHOC_STEM);

	}

	public void testGetNakamuraIdMembers(){
		assertEquals("newgroup_some_thing_else-" + SimpleGroupEsbConsumer.MEMBER_SUFFIX,
				adapter.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:" + SimpleGroupEsbConsumer.MEMBER_SUFFIX));
	}

	public void testGetNakamuraIdManagers(){
		assertEquals("newgroup_some_thing_else-" + SimpleGroupEsbConsumer.MANAGER_SUFFIX,
				adapter.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:" + SimpleGroupEsbConsumer.MANAGER_SUFFIX));
	}

	public void testGetNakamuraIdInvalid(){
		assertNull(adapter.getNakamuraGroupId(PROVISIONED_STEM + ":newgroup:some:thing:else:sssss"));
	}
}
