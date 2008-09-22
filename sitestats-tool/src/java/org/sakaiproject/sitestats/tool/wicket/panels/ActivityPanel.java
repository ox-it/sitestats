package org.sakaiproject.sitestats.tool.wicket.panels;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.IAjaxCallDecorator;
import org.apache.wicket.ajax.calldecorator.AjaxCallDecorator;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxLink;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.internal.HtmlHeaderContainer;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.sitestats.api.PrefsData;
import org.sakaiproject.sitestats.api.StatsManager;
import org.sakaiproject.sitestats.api.SummaryActivityTotals;
import org.sakaiproject.sitestats.api.SummaryVisitsTotals;
import org.sakaiproject.sitestats.tool.facade.SakaiFacade;
import org.sakaiproject.sitestats.tool.wicket.components.AjaxLazyLoadImage;
import org.sakaiproject.sitestats.tool.wicket.pages.OverviewPage;


/**
 * @author Nuno Fernandes
 */
public class ActivityPanel extends Panel {
	private static final long		serialVersionUID		= 1L;
	private static Log				LOG						= LogFactory.getLog(ActivityPanel.class);

	/** Inject Sakai facade */
	@SpringBean
	private transient SakaiFacade	facade;

	/** The site id. */
	private String					siteId					= null;

	/** Selected chart view. */
	private String					selectedView			= StatsManager.VIEW_WEEK;
	private String					selectedType			= StatsManager.CHARTTYPE_PIE;
	private int						selectedWidth			= 0;
	private int						selectedHeight			= 0;
	private int						maximizedWidth			= 0;
	private int						maximizedHeight			= 0;

	// UI Componets
	private WebMarkupContainer		selectors				= null;
	private IndicatingAjaxLink		lastWeekLink			= null;
	private WebMarkupContainer		lastWeekLabel			= null;
	private IndicatingAjaxLink		lastMonthLink			= null;
	private WebMarkupContainer		lastMonthLabel			= null;
	private IndicatingAjaxLink		lastYearLink			= null;
	private WebMarkupContainer		lastYearLabel			= null;
	private IndicatingAjaxLink		pieLink					= null;
	private WebMarkupContainer		pieLabel				= null;
	private IndicatingAjaxLink		barLink					= null;
	private WebMarkupContainer		barLabel				= null;
	private WebMarkupContainer		chartLegendContainer	= null;
	private WebMarkupContainer		pieChartLegend			= null;
	private WebMarkupContainer		barChartLegend			= null;


	private WebMarkupContainer		activityTable			= null;
	private Fragment 				activityTableFrag		= null;
	private WebMarkupContainer		activityTableContainer = null;

	private AjaxLazyLoadImage		chart					= null;

	/**
	 * Default constructor.
	 * @param id The wicket:id
	 * @param siteId The related site id
	 */
	public ActivityPanel(String id, String siteId) {
		super(id);
		this.siteId = siteId;
		setRenderBodyOnly(true);
		setOutputMarkupId(true);
		renderChart();
		renderSelectors();
		renderTable();
	}

	/** Render chart selectors. */
	private void renderSelectors() {
		selectors = new WebMarkupContainer("selectors");
		selectors.setOutputMarkupId(true);
		add(selectors);
		// Last week
		boolean inLastWeek = StatsManager.VIEW_WEEK.equals(getSelectedView());
		lastWeekLink = new IndicatingAjaxLink("lastWeekLink") {
			@Override
			public void onClick(AjaxRequestTarget target) {
				setSelectedView(StatsManager.VIEW_WEEK);
				target.addComponent(selectors);
				target.addComponent(chart);
				target.addComponent(chartLegendContainer);
				//target.appendJavascript("setMainFrameHeightNoScroll( window.name, 642 )");
			}			
		};
		lastWeekLink.setVisible(!inLastWeek);
		selectors.add(lastWeekLink);
		lastWeekLabel = new WebMarkupContainer("lastWeekLabel");
		lastWeekLabel.setVisible(inLastWeek);
		selectors.add(lastWeekLabel);

		// Last month
		boolean inLastMonth = StatsManager.VIEW_MONTH.equals(getSelectedView());
		lastMonthLink = new IndicatingAjaxLink("lastMonthLink") {
			@Override
			public void onClick(AjaxRequestTarget target) {
				setSelectedView(StatsManager.VIEW_MONTH);
				target.addComponent(selectors);
				target.addComponent(chart);
				target.addComponent(chartLegendContainer);
				//target.appendJavascript("setMainFrameHeightNoScroll( window.name, 642 )");
			}
		};
		lastMonthLink.setVisible(!inLastMonth);
		selectors.add(lastMonthLink);
		lastMonthLabel = new WebMarkupContainer("lastMonthLabel");
		lastMonthLabel.setVisible(inLastMonth);
		selectors.add(lastMonthLabel);

		// Last year
		boolean inLastYear = StatsManager.VIEW_YEAR.equals(getSelectedView());
		lastYearLink = new IndicatingAjaxLink("lastYearLink") {
			@Override
			public void onClick(AjaxRequestTarget target) {
				setSelectedView(StatsManager.VIEW_YEAR);
				target.addComponent(selectors);
				target.addComponent(chart);
				target.addComponent(chartLegendContainer);
				//target.appendJavascript("setMainFrameHeightNoScroll( window.name, 642 )");
			}

		};
		lastYearLink.setVisible(!inLastYear);
		selectors.add(lastYearLink);
		lastYearLabel = new WebMarkupContainer("lastYearLabel");
		lastYearLabel.setVisible(inLastYear);
		selectors.add(lastYearLabel);

		// By Tool
		boolean inPie = StatsManager.CHARTTYPE_PIE.equals(getSelectedType());
		pieLink = new IndicatingAjaxLink("pieLink") {
			@Override
			public void onClick(AjaxRequestTarget target) {
				setSelectedType(StatsManager.CHARTTYPE_PIE);
				target.addComponent(selectors);
				target.addComponent(chart);
				target.addComponent(chartLegendContainer);
				//target.appendJavascript("setMainFrameHeightNoScroll( window.name, 642 )");
			}

		};
		pieLink.setVisible(!inPie);
		selectors.add(pieLink);
		pieLabel = new WebMarkupContainer("pieLabel");
		pieLabel.setVisible(inPie);
		selectors.add(pieLabel);

		// By Date
		boolean inBar = StatsManager.CHARTTYPE_BAR.equals(getSelectedType());
		barLink = new IndicatingAjaxLink("barLink") {
			@Override
			public void onClick(AjaxRequestTarget target) {
				setSelectedType(StatsManager.CHARTTYPE_BAR);
				target.addComponent(selectors);
				target.addComponent(chart);
				target.addComponent(chartLegendContainer);
				//target.appendJavascript("setMainFrameHeightNoScroll( window.name, 642 )");
			}

		};
		barLink.setVisible(!inBar);
		selectors.add(barLink);
		barLabel = new WebMarkupContainer("barLabel");
		barLabel.setVisible(inBar);
		selectors.add(barLabel);
	}

	/** Render table. */
	private void renderTable() {
		activityTableContainer = new WebMarkupContainer("activityTableContainer");
		activityTableContainer.setOutputMarkupId(true);
		add(activityTableContainer);
		
		WebMarkupContainer activityTablePre = new WebMarkupContainer("activityTablePre");
		activityTablePre.setOutputMarkupId(true);
		activityTableContainer.add(activityTablePre);
	}
	
	/** Render table data. */
	public Component renderTableData() {
		SummaryActivityTotals summaryActivityTotals = facade.getStatsManager().getSummaryActivityTotals(siteId);

		activityTableContainer.removeAll();
		activityTableFrag = new Fragment("activityTablePre", "activityTableFrag");
		activityTableFrag.setOutputMarkupId(true);
		activityTableContainer.add(activityTableFrag);
		activityTable = new WebMarkupContainer("activityTable");
		activityTableFrag.add(activityTable);

		// Total visits
		final Label totalActivity = new Label("totalActivity", String.valueOf(summaryActivityTotals.getTotalActivity()));
		activityTable.add(totalActivity);

		// Average Visits
		StringBuilder avgActivityStr = new StringBuilder();
		avgActivityStr.append(summaryActivityTotals.getLast7DaysActivityAverage());
		avgActivityStr.append('/');
		avgActivityStr.append(summaryActivityTotals.getLast30DaysActivityAverage());
		avgActivityStr.append('/');
		avgActivityStr.append(summaryActivityTotals.getLast365DaysActivityAverage());
		final Label avgActivity = new Label("avgActivity", avgActivityStr.toString());
		activityTable.add(avgActivity);
		
		return activityTableContainer;
	}

	/** Render chart. */
	@SuppressWarnings("serial")
	public void renderChart() {
		// chart
		chart = new AjaxLazyLoadImage("chart", null, OverviewPage.class) {
			@Override
			public BufferedImage getBufferedImage() {
				return getChartImage();
			}

			@Override
			public BufferedImage getBufferedMaximizedImage() {
				return getChartImage(maximizedWidth, maximizedHeight);
			}
		};
		add(chart);
		
		// chart legend
		boolean inPie = StatsManager.CHARTTYPE_PIE.equals(selectedType);
		chartLegendContainer = new WebMarkupContainer("chartLegendContainer");
		chartLegendContainer.setOutputMarkupId(true);
		add(chartLegendContainer);
		pieChartLegend = new WebMarkupContainer("pieChartLegend");
		pieChartLegend.setVisible(inPie);
		pieChartLegend.setOutputMarkupId(true);
		chartLegendContainer.add(pieChartLegend);		
		barChartLegend = new WebMarkupContainer("barChartLegend");
		barChartLegend.setVisible(!inPie);
		barChartLegend.setOutputMarkupId(true);
		chartLegendContainer.add(barChartLegend);
	}
	
	public BufferedImage getChartImage() {
		return getChartImage(selectedWidth, selectedHeight);
	}
	
	public BufferedImage getChartImage(int width, int height) {
		PrefsData prefsData = facade.getStatsManager().getPreferences(siteId, false);
		int _width = (width <= 0) ? 350 : width;
		int _height = (height <= 0) ? 200: height;
		BufferedImage img = facade.getChartService().generateActivityChart(
				siteId, selectedView, selectedType, 
				_width, _height,
				prefsData.isChartIn3D(), prefsData.getChartTransparency(),
				prefsData.isItemLabelsVisible());
		return img;
	}

	public final String getSelectedView() {
		return selectedView;
	}

	public final void setSelectedView(String selectedView) {
		this.selectedView = selectedView;
		
		boolean inLastWeek = StatsManager.VIEW_WEEK.equals(selectedView);
		boolean inLastMonth = StatsManager.VIEW_MONTH.equals(selectedView);
		boolean inLastYear = StatsManager.VIEW_YEAR.equals(selectedView);
		lastWeekLink.setVisible(!inLastWeek);
		lastWeekLabel.setVisible(inLastWeek);
		lastMonthLink.setVisible(!inLastMonth);
		lastMonthLabel.setVisible(inLastMonth);
		lastYearLink.setVisible(!inLastYear);
		lastYearLabel.setVisible(inLastYear);
		chart.renderImage();
	}

	public final String getSelectedType() {
		return selectedType;
	}

	public final void setSelectedType(String selectedType) {
		this.selectedType = selectedType;

		boolean inPie = StatsManager.CHARTTYPE_PIE.equals(selectedType);
		boolean inBar = StatsManager.CHARTTYPE_BAR.equals(selectedType);
		pieLink.setVisible(!inPie);
		pieLabel.setVisible(inPie);
		barLink.setVisible(!inBar);
		barLabel.setVisible(inBar);	
		pieChartLegend.setVisible(inPie);
		barChartLegend.setVisible(inBar);
		chart.renderImage();
	}

	public Collection<Component> setChartSize(int width, int height, int maximizedWidth, int maximizedHeight) {
		selectedWidth = width;
		selectedHeight = height;
		this.maximizedWidth = maximizedWidth;
		this.maximizedHeight = maximizedHeight;
		chart.renderImage();
		List<Component> list = new ArrayList<Component>();
		list.add(chart);
		list.add(chartLegendContainer);
		return list;
	}

}
