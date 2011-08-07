package org.sakaiproject.nakamura.grouper.changelog;
import junit.framework.TestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sakaiproject.nakamura.grouper.changelog.MutableGroup;

import edu.internet2.middleware.grouper.Group;

public class MutableGroupTestCase extends TestCase {
	
	Group group;
	
	public void setUp(){
		group = mock(Group.class);
		when(group.getName()).thenReturn("some:original:stem:extension");
		when(group.getExtension()).thenReturn("extension");
		when(group.getParentStemName()).thenReturn("some:original:stem");
		when(group.getDescription()).thenReturn("Original Description");
	}

	public void testNameChange(){
		MutableGroup g = new MutableGroup(group);
		g.setName("some:test:stem:extension1");
		assertEquals("some:test:stem:extension1", g.getName());
	}
	
	public void testNameChangeExtension(){
		MutableGroup g = new MutableGroup(group);
		g.setName("some:test:stem:extension1");
		assertEquals("extension1", g.getExtension());
	}
	
	public void testNameChangeParentStemName(){
		MutableGroup g = new MutableGroup(group);
		g.setName("some:test:stem:extension1");
		assertEquals("some:test:stem", g.getParentStemName());
	}
	
	public void testDescription(){
		MutableGroup g = new MutableGroup(group);
		assertEquals("Original Description", g.getDescription());
	}
}
