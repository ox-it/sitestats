package org.sakaiproject.sitestats.tool.wicket.pages;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.sitestats.tool.facade.SakaiFacade;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.util.ResourceLoader;


public class BasePage extends WebPage implements IHeaderContributor {
	
	private static final long		serialVersionUID	= 1L;
	protected static final String	HEADSCRIPTS			= "/library/js/headscripts.js";
	protected static final String	BODY_ONLOAD_ADDTL	= "setMainFrameHeight( window.name )";
	protected static final String	LAST_PAGE			= "lastSiteStatsPage";

	@SpringBean
	private transient SakaiFacade facade;
	
	public BasePage(){
		// Set Sakai Locale
		ResourceLoader rl = new ResourceLoader();
		getSession().setLocale(rl.getLocale());
	}

	public void renderHead(IHeaderResponse response) {
		// compute sakai skin
		String skinRepo = ServerConfigurationService.getString("skin.repo");
		response.renderCSSReference(skinRepo + "/tool_base.css");
		response.renderCSSReference(getToolSkinCSS(skinRepo));

		// include sakai headscripts and resize iframe on load
		response.renderJavascriptReference(HEADSCRIPTS);
		response.renderOnLoadJavascript(BODY_ONLOAD_ADDTL);

		// include (this) tool style (CSS)
		response.renderCSSReference("/sakai-sitestats-tool/css/sitestats.css");
	}

	public String getPortalSkinCSS() {
		return getPortalSkinCSS(null);
	}
	
	private String getPortalSkinCSS(String skinRepo) {
		String skin = null;
		if(skinRepo == null) {
			skinRepo = ServerConfigurationService.getString("skin.repo");
		}
		try{
			skin = SiteService.findTool(SessionManager.getCurrentToolSession().getPlacementId()).getSkin();
		}catch(Exception e){
			skin = ServerConfigurationService.getString("skin.default");
		}

		if(skin == null){
			skin = ServerConfigurationService.getString("skin.default");
		}

		return skinRepo + "/" + skin + "/portal.css";
	}

	public String getToolSkinCSS() {
		return getToolSkinCSS(null);
	}

	protected String getToolSkinCSS(String skinRepo) {
		String skin = null;
		if(skinRepo == null) {
			skinRepo = ServerConfigurationService.getString("skin.repo");
		}
		try{
			skin = SiteService.findTool(SessionManager.getCurrentToolSession().getPlacementId()).getSkin();
		}catch(Exception e){
			skin = ServerConfigurationService.getString("skin.default");
		}

		if(skin == null){
			skin = ServerConfigurationService.getString("skin.default");
		}

		return skinRepo + "/" + skin + "/tool.css";
	}

	protected Label newResourceLabel(String id, Component component) {
		return new Label(id, new StringResourceModel(id, component, null));
	}

	public String getResourceModel(String resourceKey, IModel model) {
		return new StringResourceModel(resourceKey, this, model).getString();
	}
}