package org.sakaiproject.sitestats.impl;

import java.util.List;
import java.util.ResourceBundle;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.ObjectCreationFactory;
import org.sakaiproject.sitestats.api.ToolInfo;
import org.sakaiproject.sitestats.api.ToolFactory;
import org.xml.sax.Attributes;


public class ToolFactoryImpl implements ToolFactory, ObjectCreationFactory {
	protected ResourceBundle	msgs	= ResourceBundle.getBundle("org.sakaiproject.sitestats.impl.bundle.Messages");

	public ToolInfo createTool(String toolId) {
		return new ToolInfoImpl(toolId);
	}
	
	public ToolInfo createTool(String toolId, List<String> additionalToolIds) {
		return new ToolInfoImpl(toolId, additionalToolIds);
	}
	
	public Object createObject(Attributes attributes) throws Exception {
		String toolId = attributes.getValue("toolId");
		String selected = attributes.getValue("selected");
		String additionalToolIds = attributes.getValue("additionalToolIds");

		if(toolId == null){ throw new Exception("Mandatory toolId attribute not present on tool tag."); }
		ToolInfo toolInfo = new ToolInfoImpl(toolId.trim());
		toolInfo.setSelected(Boolean.parseBoolean(selected));
		if(additionalToolIds != null) {
			toolInfo.setAdditionalToolIdsStr(additionalToolIds);
		}
		return toolInfo;
	}

	public Digester getDigester() {
		return null;
	}

	public void setDigester(Digester digester) {
	}

}
