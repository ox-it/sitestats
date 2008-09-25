package org.sakaiproject.sitestats.tool.wicket.panels;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.internal.HtmlHeaderContainer;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.sitestats.api.PrefsData;
import org.sakaiproject.sitestats.api.StatsManager;
import org.sakaiproject.sitestats.api.event.EventInfo;
import org.sakaiproject.sitestats.api.event.ToolInfo;
import org.sakaiproject.sitestats.api.parser.EventParserTip;
import org.sakaiproject.sitestats.tool.facade.SakaiFacade;
import org.sakaiproject.sitestats.tool.wicket.components.CSSFeedbackPanel;
import org.sakaiproject.sitestats.tool.wicket.components.EventRegistryTree;

/**
 * @author Nuno Fernandes
 */

public class PreferencesPanel extends Panel {
	private static final long		serialVersionUID			= 1L;

	/** Inject Sakai facade */
	@SpringBean
	private transient SakaiFacade	facade;

	/** Site ID */
	private String					siteId						= null;
	private String					currentSiteId				= null;

	// UI
	private FeedbackPanel			feedback					= null;
	private List<String>			chartTransparencyChoices	= null;
	private EventRegistryTree 		eventRegistryTree 			= null;


	// Model
	private PrefsData				prefsdata					= null;
	
	/**
	 * Default constructor.
	 * @param id The wicket:id
	 */
	public PreferencesPanel(String id) {
		this(id, null);
	}

	/**
	 * Constructor for data relative to a given site.
	 * @param id The wicket:id
	 * @param siteId The related site id
	 */
	public PreferencesPanel(String id, String siteId) {
		super(id);
		// site id
		this.siteId = siteId;
		currentSiteId = facade.getToolManager().getCurrentPlacement().getContext();
		if(this.siteId == null){
			this.siteId = currentSiteId;
		}
		setModel(new CompoundPropertyModel(this));
		renderBody();
	}

	@Override
	public void renderHead(HtmlHeaderContainer container) {
		container.getHeaderResponse().renderJavascriptReference("/library/js/jquery.js");
		container.getHeaderResponse().renderJavascriptReference("/sakai-sitestats-tool/script/common.js");
		super.renderHead(container);
	}

	@SuppressWarnings("serial")
	private void renderBody() {
		Form form = new Form("preferencesForm");
		add(form);
		feedback = new CSSFeedbackPanel("messages");
		form.add(feedback);
		
		
		// Section: General
		CheckBox listToolEventsOnlyAvailableInSite = new CheckBox("listToolEventsOnlyAvailableInSite");
		form.add(listToolEventsOnlyAvailableInSite);
		
		
		// Section: Chart
		WebMarkupContainer chartPrefs = new WebMarkupContainer("chartPrefs");
		boolean chartPrefsVisible = facade.getStatsManager().isEnableSiteVisits() || facade.getStatsManager().isEnableSiteActivity();
		chartPrefs.setVisible(chartPrefsVisible);
		form.add(chartPrefs);
		CheckBox chartIn3D = new CheckBox("chartIn3D");
		chartPrefs.add(chartIn3D);
		CheckBox itemLabelsVisible = new CheckBox("itemLabelsVisible");
		chartPrefs.add(itemLabelsVisible);
		chartTransparencyChoices = new ArrayList<String>();
		for(int i=100; i>=10; i-=10) {
			chartTransparencyChoices.add(Integer.toString(i));
		}
		DropDownChoice chartTransparency = new DropDownChoice("chartTransparency", chartTransparencyChoices, new IChoiceRenderer() {
			public Object getDisplayValue(Object object) {
				return (String) object + "%";
			}
			public String getIdValue(Object object, int index) {
				return (String) object;
			}			
		});
		chartPrefs.add(chartTransparency);
		
		
		// Section: Activity Definition
		eventRegistryTree = new EventRegistryTree("eventRegistryTree", getPrefsdata().getToolEventsDef()) {
			@Override
			public boolean isToolSuported(final ToolInfo toolInfo) {
				if(facade.getStatsManager().isEventContextSupported()){
					return true;
				}else{
					List<ToolInfo> siteTools = facade.getEventRegistryService().getEventRegistry(siteId, getPrefsdata().isListToolEventsOnlyAvailableInSite());
					Iterator<ToolInfo> i = siteTools.iterator();
					while (i.hasNext()){
						ToolInfo t = i.next();
						if(t.getToolId().equals(toolInfo.getToolId())){
							EventParserTip parserTip = t.getEventParserTip();
							if(parserTip != null && parserTip.getFor().equals(StatsManager.PARSERTIP_FOR_CONTEXTID)){
								return true;
							}
						}
					}
				}
				return false;
			}
		};
		form.add(eventRegistryTree);
		
		
		// Bottom Buttons
		Button update = new Button("update") {
			@Override
			public void onSubmit() {
				savePreferences();
				prefsdata = null;
				super.onSubmit();
			}
		};
		update.setDefaultFormProcessing(true);
		form.add(update);
		Button cancel = new Button("cancel") {
			@Override
			public void onSubmit() {
				prefsdata = null;
				super.onSubmit();
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);
	}
	
	private PrefsData getPrefsdata() {
		if(prefsdata == null) {
			prefsdata = facade.getStatsManager().getPreferences(siteId, true);
		}
		return prefsdata;
	}

	private void savePreferences() {
		getPrefsdata().setToolEventsDef((List<ToolInfo>) eventRegistryTree.getEventRegistry());
		boolean opOk = facade.getStatsManager().setPreferences(siteId, getPrefsdata());		
		if(opOk){
			info((String) new ResourceModel("prefs_updated").getObject());
		}else{
			error((String) new ResourceModel("prefs_not_updated").getObject());
		}
	}

	public void setListToolEventsOnlyAvailableInSite(boolean listToolEventsOnlyAvailableInSite) {
		prefsdata.setListToolEventsOnlyAvailableInSite(listToolEventsOnlyAvailableInSite);
	}

	public boolean isListToolEventsOnlyAvailableInSite() {
		return getPrefsdata().isListToolEventsOnlyAvailableInSite();
	}
	
	public void setChartIn3D(boolean chartIn3D) {
		prefsdata.setChartIn3D(chartIn3D);
	}

	public boolean isChartIn3D() {
		return getPrefsdata().isChartIn3D();
	}

	public void setItemLabelsVisible(boolean itemLabelsVisible) {
		prefsdata.setItemLabelsVisible(itemLabelsVisible);
	}

	public boolean isItemLabelsVisible() {
		return getPrefsdata().isItemLabelsVisible();
	}
	
	public void setChartTransparency(String value) {
		float converted = (float) round(Double.parseDouble(value)/100,1);
		getPrefsdata().setChartTransparency(converted);
	}
	
	public String getChartTransparency() {
		return Integer.toString((int) round(getPrefsdata().getChartTransparency()*100,0) );
	}
	
	private static double round(double val, int places) {
		long factor = (long) Math.pow(10, places);
		// Shift the decimal the correct number of places to the right.
		val = val * factor;
		// Round to the nearest integer.
		long tmp = Math.round(val);
		// Shift the decimal the correct number of places back to the left.
		return (double) tmp / factor;
	}
}

