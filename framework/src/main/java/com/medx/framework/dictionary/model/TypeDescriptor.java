package com.medx.framework.dictionary.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement
@XmlType(propOrder={})
public class TypeDescriptor extends DictionaryEntry {
	private static final long serialVersionUID = 6113820571290881884L;
	
	@XmlElement(required=true, name="class")
	private String javaClass;
	
	public String getJavaClassName() {
		return javaClass;
	}

	public void setJavaClassName(String javaClass) {
		this.javaClass = javaClass;
	}
}
