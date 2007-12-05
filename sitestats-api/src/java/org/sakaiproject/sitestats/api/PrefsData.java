package org.sakaiproject.sitestats.api;

import java.util.List;

public interface PrefsData {
	
	public List<ToolInfo> getToolEventsDef();	
	public void setToolEventsDef(List<ToolInfo> toolEventsDef);
	public List<String> getToolEventsStringList();
	
	public boolean isListToolEventsOnlyAvailableInSite();
	public void setListToolEventsOnlyAvailableInSite(boolean listToolEventsOnlyAvailableInSite);
	
	public void setChartIn3D(boolean value);	
	public boolean isChartIn3D();
	
	public void setChartTransparency(float value);
	public void setChartTransparency(String value);
	public float getChartTransparency();
	
	public void setItemLabelsVisible(boolean itemLabelsVisible);	
	public boolean isItemLabelsVisible();
	
	public String toXmlPrefs();
}