package org.sakaiproject.sitestats.impl.event;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.MissingResourceException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.sitestats.api.event.EventRegistry;
import org.sakaiproject.sitestats.api.event.ToolInfo;
import org.sakaiproject.sitestats.impl.parser.DigesterUtil;
import org.sakaiproject.util.ResourceLoader;
import org.springframework.core.io.ClassPathResource;


public class FileEventRegistry implements EventRegistry {
	/** Static fields */
	public final static String		TOOL_EVENTS_DEF_FILE				= "toolEventsDef.xml";
	private Log						LOG									= LogFactory.getLog(FileEventRegistry.class);
	private static ResourceLoader	msgs								= new ResourceLoader("Events");

	/** File based event registry */
	private List<ToolInfo>			eventRegistry						= null;

	/** Spring bean members */
	private String					customEventRegistryFile				= null;
	private String					customEventRegistryAdditionsFile	= null;
	private String					customEventRegistryRemovalsFile		= null;
	
	
	// ################################################################
	// Spring bean methods
	// ################################################################
	public void setToolEventsDefinitionFile(String file) {
		customEventRegistryFile = file;
	}
	
	public void setToolEventsAddDefinitionFile(String file) {
		customEventRegistryAdditionsFile = file;
	}
	
	public void setToolEventsRemoveDefinitionFile(String file) {
		customEventRegistryRemovalsFile = file;
	}
	

	// ################################################################
	// Event Registry methods
	// ################################################################
	// was getAllToolEventsDefinition()
	public List<ToolInfo> getEventRegistry() {
		if(eventRegistry == null){
			// Load event registry file
			loadEventRegistryFile();
		}
		return eventRegistry;
	}
	
	public String getEventName(String eventId) {
		String eventName = null;
		try{
			eventName = msgs.getString(eventId);//, eventId);
		}catch(MissingResourceException e){
			eventName = null;
		}		
		return eventName;
	}
	

	// ################################################################
	// File Event Registry Load
	// ################################################################
	private void loadEventRegistryFile() {
		boolean customEventRegistryFileLoaded = false;
		
		// user-specified tool events definition
		if(customEventRegistryFile != null) {
			File customDefs = new File(customEventRegistryFile);
			if(customDefs.exists()){
				try{
					LOG.info("init(): - loading custom event registry from: " + customDefs.getAbsolutePath());
					eventRegistry = DigesterUtil.parseToolEventsDefinition(new FileInputStream(customDefs));
					customEventRegistryFileLoaded = true;
				}catch(Throwable t){
					LOG.warn("init(): - trouble loading event registry from : " + customDefs.getAbsolutePath(), t);
				}
			}else {
				LOG.warn("init(): - custom event registry file not found: "+customDefs.getAbsolutePath());
			}
		}
		
		// default tool events definition
		if(!customEventRegistryFileLoaded){
			ClassPathResource defaultDefs = new ClassPathResource("org/sakaiproject/sitestats/config/" + FileEventRegistry.TOOL_EVENTS_DEF_FILE);
			try{
				LOG.info("init(): - loading default event registry from: " + defaultDefs.getPath()+". A custom one for adding/removing events can be specified in sakai.properties with the property: toolEventsDefinitionFile@org.sakaiproject.sitestats.api.StatsManager=${sakai.home}/toolEventsdef.xml.");
				eventRegistry = DigesterUtil.parseToolEventsDefinition(defaultDefs.getInputStream());
			}catch(Throwable t){
				LOG.error("init(): - trouble loading default event registry from : " + defaultDefs.getPath(), t);
			}
		}
		
		// add user-specified tool
		List<ToolInfo> additions = null;
		if(customEventRegistryAdditionsFile != null) {
			File customDefs = new File(customEventRegistryAdditionsFile);
			if(customDefs.exists()){
				try{
					LOG.info("init(): - loading custom event registry additions from: " + customDefs.getAbsolutePath());
					additions = DigesterUtil.parseToolEventsDefinition(new FileInputStream(customDefs));
				}catch(Throwable t){
					LOG.warn("init(): - trouble loading custom event registry additions from : " + customDefs.getAbsolutePath(), t);
				}
			}else {
				LOG.warn("init(): - custom event registry additions file not found: "+customDefs.getAbsolutePath());
			}
		}
		if(additions != null)
			eventRegistry = EventUtil.addToEventRegistry(additions, false, eventRegistry);

		// remove user-specified tool and/or events
		List<ToolInfo> removals = null;
		if(customEventRegistryRemovalsFile != null) {
			File customDefs = new File(customEventRegistryRemovalsFile);
			if(customDefs.exists()){
				try{
					LOG.info("init(): - loading custom event registry removals from: " + customDefs.getAbsolutePath());
					removals = DigesterUtil.parseToolEventsDefinition(new FileInputStream(customDefs));
				}catch(Throwable t){
					LOG.warn("init(): - trouble loading custom event registry removals from : " + customDefs.getAbsolutePath(), t);
				}
			}else {
				LOG.warn("init(): - custom event registry removals file not found: "+customDefs.getAbsolutePath());
			}
		}
		if(removals != null)
			eventRegistry = EventUtil.removeFromEventRegistry(removals, eventRegistry);		
		
		// debug: print resulting list
//		LOG.info("-------- Printing resulting eventRegistry list:");
//		Iterator<ToolInfo> iT = eventRegistry.iterator();
//		while(iT.hasNext()) LOG.info(iT.next().toString());
//		LOG.info("------------------------------------------------------");
	}
}
