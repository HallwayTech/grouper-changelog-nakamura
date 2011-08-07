package org.sakaiproject.nakamura.grouper.changelog;

import edu.internet2.middleware.grouper.Group;

public class MutableGroup extends Group {

	String name;
	String extension;
	String description;
	String parentStemName;
	
	public MutableGroup(Group g){
		if (g != null){
			name = g.getName();
			extension = g.getExtension();
			description = g.getDescription();
			parentStemName = g.getParentStemName();
		}
	}
	
	public void setName(String name){
		this.name = name;
		this.extension = name.substring(name.lastIndexOf(':') + 1);
		this.parentStemName = name.substring(0, name.lastIndexOf(':'));
	}

	public String getName() {
		return name;
	}

	public String getExtension() {
		return extension;
	}

	public String getDescription() {
		return description;
	}
	
	public String getParentStemName() {
		return parentStemName;
	}
}
