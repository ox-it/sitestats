package org.sakaiproject.sitestats.tool.wicket.pages;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.sitestats.tool.facade.SakaiFacade;
import org.sakaiproject.sitestats.tool.wicket.components.AdminMenu;
import org.sakaiproject.sitestats.tool.wicket.components.SakaiDataTable;
import org.sakaiproject.sitestats.tool.wicket.components.SakaiNavigatorSearch;
import org.sakaiproject.sitestats.tool.wicket.components.SiteLinkPanel;
import org.sakaiproject.sitestats.tool.wicket.providers.StatisticableSitesDataProvider;


/**
 * @author Nuno Fernandes
 */
public class AdminPage extends BasePage {
	private static final long		serialVersionUID	= 1L;

	/** Inject Sakai facade */
	@SpringBean
	private transient SakaiFacade	facade;
	
	public AdminPage() {
		this(null);
	}

	public AdminPage(PageParameters params) {
		String siteId = facade.getToolManager().getCurrentPlacement().getContext();
		boolean allowed = facade.getStatsAuthz().isUserAbleToViewSiteStatsAdmin(siteId);
		if(allowed){
			renderBody();
		}else{
			redirectToInterceptPage(new NotAuthorizedPage());
		}
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		// response.renderJavascriptReference("/library/js/jquery.js");
		super.renderHead(response);
	}

	@SuppressWarnings("serial")
	private void renderBody() {
		final StatisticableSitesDataProvider dataProvider = new StatisticableSitesDataProvider();
		
		add(new AdminMenu("menu"));

		// Search
		add(new SakaiNavigatorSearch("search", dataProvider));
		
		// Site types
		List<String> choices = new ArrayList<String>();
		choices.add(StatisticableSitesDataProvider.SITE_TYPE_ALL);
		List<String> types = facade.getSiteService().getSiteTypes();
		for(String t : types) {
			choices.add(t);	
		}
		DropDownChoice siteTypes = new DropDownChoice("siteTypes", choices, new IChoiceRenderer() {
			public Object getDisplayValue(Object object) {
				String value = (String) object;
				if(value != null && value.equals(StatisticableSitesDataProvider.SITE_TYPE_ALL)) {
					return new ResourceModel("all").getObject();
				}else{
					return object;
				}
			}
			public String getIdValue(Object object, int index) {
				return (String) object;
			}
		}) {
			@Override
			protected boolean wantOnSelectionChangedNotifications() {
				return true;
			}

			@Override
			protected void onSelectionChanged(Object newSelection) {
				setRedirect(true);
				setResponsePage(getPage());
				super.onSelectionChanged(newSelection);
			}
			
		};
		siteTypes.setModel(new PropertyModel(dataProvider, "siteType"));
		add(siteTypes);
		
		// Table columns
		List<IColumn> columns = new ArrayList<IColumn>();
		columns.add(new PropertyColumn(new ResourceModel("th_title"), StatisticableSitesDataProvider.COL_TITLE, StatisticableSitesDataProvider.COL_TITLE) {
			@Override
			public void populateItem(Item item, String componentId, IModel model) {
				item.add(new SiteLinkPanel(componentId, model));
			}
		});
		columns.add(new PropertyColumn(new ResourceModel("th_type"), StatisticableSitesDataProvider.COL_TYPE, StatisticableSitesDataProvider.COL_TYPE));
		columns.add(new PropertyColumn(new ResourceModel("th_status"), StatisticableSitesDataProvider.COL_STATUS, StatisticableSitesDataProvider.COL_STATUS) {
			@Override
			public void populateItem(Item item, String componentId, IModel model) {
				final boolean isPublished = ((Site) model.getObject()).isPublished();
				ResourceModel modelStr = null;
				if(isPublished) {
					modelStr = new ResourceModel("site_published");
				}else{
					modelStr = new ResourceModel("site_unpublished");
				}
				item.add(new Label(componentId, modelStr));
			}
		});
		
		// Table
		add(new SakaiDataTable("table", columns, dataProvider));
	}
	
}