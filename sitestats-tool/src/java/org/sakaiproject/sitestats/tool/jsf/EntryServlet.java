package org.sakaiproject.sitestats.tool.jsf;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.sitestats.api.StatsManager;


public class EntryServlet extends HttpServlet {
	private static final long	serialVersionUID	= 1L;
	private static Log			LOG					= LogFactory.getLog(EntryServlet.class);
	private StatsManager		m_sm				= (StatsManager) ComponentManager.get(StatsManager.class);

	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
		doGet(req, resp);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		try{
			StringBuilder path = new StringBuilder(request.getContextPath());
			if(m_sm.isEnableSiteVisits() || m_sm.isEnableSiteActivity()){
				path.append("/overview.jsf");
			}else{
				path.append("/reports.jsf");
			}
			String queryString = request.getQueryString();
			if(queryString != null){
				path.append("?").append(queryString);
			}
			response.sendRedirect(path.toString());
		}catch(Exception e){
			LOG.warn("Unable to redirect to initial page...", e);
		}
	}

}