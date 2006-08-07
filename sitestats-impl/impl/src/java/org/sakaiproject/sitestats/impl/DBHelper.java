package org.sakaiproject.sitestats.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.service.framework.sql.SqlService;


public class DBHelper {
	private SqlService	M_sql;
	private Log			LOG						= LogFactory.getLog(DBHelper.class);
	private boolean		notifiedIndexesUpdate	= false;

	public DBHelper(SqlService M_sql) {
		this.M_sql = M_sql;
	}

	public void updateIndexes() {
		notifiedIndexesUpdate = false;
		try{
			Connection c = M_sql.borrowConnection();
			List sstEventsIxs = listIndexes(c, "SST_EVENTS");
			List sstResourcesIxs = listIndexes(c, "SST_RESOURCES");
			List sstSiteActivityIxs = listIndexes(c, "SST_SITEACTIVITY");
			List sstSiteVisitsIxs = listIndexes(c, "SST_SITEVISITS");

			// SST_EVENTS
			if(sstEventsIxs.contains("SITE_ID_IX")) renameIndex(c, "SITE_ID_IX", "SST_EVENTS_SITE_ID_IX", "SITE_ID", "SST_EVENTS");
			else if(!sstEventsIxs.contains("SST_EVENTS_SITE_ID_IX")) createIndex(c, "SST_EVENTS_SITE_ID_IX", "SITE_ID", "SST_EVENTS");
			if(sstEventsIxs.contains("USER_ID_IX")) renameIndex(c, "USER_ID_IX", "SST_EVENTS_USER_ID_IX", "USER_ID", "SST_EVENTS");
			else if(!sstEventsIxs.contains("SST_EVENTS_USER_ID_IX")) createIndex(c, "SST_EVENTS_USER_ID_IX", "USER_ID", "SST_EVENTS");
			if(sstEventsIxs.contains("EVENT_ID_IX")) renameIndex(c, "EVENT_ID_IX", "SST_EVENTS_EVENT_ID_IX", "EVENT_ID", "SST_EVENTS");
			else if(!sstEventsIxs.contains("SST_EVENTS_EVENT_ID_IX")) createIndex(c, "SST_EVENTS_EVENT_ID_IX", "EVENT_ID", "SST_EVENTS");
			if(sstEventsIxs.contains("DATE_ID_IX")) renameIndex(c, "DATE_ID_IX", "SST_EVENTS_DATE_ID_IX", "EVENT_DATE", "SST_EVENTS");
			else if(!sstEventsIxs.contains("SST_EVENTS_DATE_ID_IX")) createIndex(c, "SST_EVENTS_DATE_ID_IX", "EVENT_DATE", "SST_EVENTS");

			// SST_RESOURCES
			if(sstResourcesIxs.contains("SITE_ID_IX")) renameIndex(c, "SITE_ID_IX", "SST_RESOURCES_SITE_ID_IX", "SITE_ID", "SST_RESOURCES");
			else if(!sstResourcesIxs.contains("SST_RESOURCES_SITE_ID_IX")) createIndex(c, "SST_RESOURCES_SITE_ID_IX", "SITE_ID", "SST_RESOURCES");
			if(sstResourcesIxs.contains("USER_ID_IX")) renameIndex(c, "USER_ID_IX", "SST_RESOURCES_USER_ID_IX", "USER_ID", "SST_RESOURCES");
			else if(!sstResourcesIxs.contains("SST_RESOURCES_USER_ID_IX")) createIndex(c, "SST_RESOURCES_USER_ID_IX", "USER_ID", "SST_RESOURCES");
			if(sstResourcesIxs.contains("RES_ACT_IDX")) renameIndex(c, "RES_ACT_IDX", "SST_RESOURCES_RES_ACT_IDX", "RESOURCE_ACTION", "SST_RESOURCES");
			else if(!sstResourcesIxs.contains("SST_RESOURCES_RES_ACT_IDX")) createIndex(c, "SST_RESOURCES_RES_ACT_IDX", "RESOURCE_ACTION", "SST_RESOURCES");
			if(sstResourcesIxs.contains("DATE_ID_IX")) renameIndex(c, "DATE_ID_IX", "SST_RESOURCES_DATE_ID_IX", "RESOURCE_DATE", "SST_RESOURCES");
			else if(!sstResourcesIxs.contains("SST_RESOURCES_DATE_ID_IX")) createIndex(c, "SST_RESOURCES_DATE_ID_IX", "RESOURCE_DATE", "SST_RESOURCES");

			// SST_SITEACTIVITY
			if(sstSiteActivityIxs.contains("SITE_ID_IX")) renameIndex(c, "SITE_ID_IX", "SST_SITEACTIVITY_SITE_ID_IX", "SITE_ID", "SST_SITEACTIVITY");
			else if(!sstSiteActivityIxs.contains("SST_SITEACTIVITY_SITE_ID_IX")) createIndex(c, "SST_SITEACTIVITY_SITE_ID_IX", "SITE_ID", "SST_SITEACTIVITY");
			if(sstSiteActivityIxs.contains("EVENT_ID_IX")) renameIndex(c, "EVENT_ID_IX", "SST_SITEACTIVITY_EVENT_ID_IX", "EVENT_ID", "SST_SITEACTIVITY");
			else if(!sstSiteActivityIxs.contains("SST_SITEACTIVITY_EVENT_ID_IX")) createIndex(c, "SST_SITEACTIVITY_EVENT_ID_IX", "EVENT_ID", "SST_SITEACTIVITY");
			if(sstSiteActivityIxs.contains("DATE_ID_IX")) renameIndex(c, "DATE_ID_IX", "SST_SITEACTIVITY_DATE_ID_IX", "ACTIVITY_DATE", "SST_SITEACTIVITY");
			else if(!sstSiteActivityIxs.contains("SST_SITEACTIVITY_DATE_ID_IX")) createIndex(c, "SST_SITEACTIVITY_DATE_ID_IX", "ACTIVITY_DATE", "SST_SITEACTIVITY");

			// SST_SITEVISITS
			if(sstSiteVisitsIxs.contains("SITE_ID_IX")) renameIndex(c, "SITE_ID_IX", "SST_SITEVISITS_SITE_ID_IX", "SITE_ID", "SST_SITEVISITS");
			else if(!sstSiteVisitsIxs.contains("SST_SITEVISITS_SITE_ID_IX")) createIndex(c, "SST_SITEVISITS_SITE_ID_IX", "SITE_ID", "SST_SITEVISITS");
			if(sstSiteVisitsIxs.contains("DATE_ID_IX")) renameIndex(c, "DATE_ID_IX", "SST_SITEVISITS_DATE_ID_IX", "VISITS_DATE", "SST_SITEVISITS");
			else if(!sstSiteVisitsIxs.contains("SST_SITEVISITS_DATE_ID_IX")) createIndex(c, "SST_SITEVISITS_DATE_ID_IX", "VISITS_DATE", "SST_SITEVISITS");

			c.close();
		}catch(Exception e){
			LOG.error("Error while updating indexes", e);
			e.printStackTrace();
		}
	}

	private void notifyIndexesUpdate(){
		if(!notifiedIndexesUpdate)
			LOG.info("init(): updating indexes on SiteStats tables...");
		notifiedIndexesUpdate = true;
	}
	
	private List listIndexes(Connection c, String table) {
		List indexes = new ArrayList();
		String sql = null;
		int pos = 0;
		if(M_sql.getVendor().equals("mysql")){
			sql = "show indexes from " + table;
			pos = 3;
		}else if(M_sql.getVendor().equals("oracle")){
			sql = "select * from all_indexes where table_name = '" + table + "'";
			pos = 2;
		}
		try{
			Statement s = c.createStatement();
			ResultSet rs = s.executeQuery(sql);
			while (rs.next()){
				String ixName = rs.getString(pos);
				indexes.add(ixName);
			}
			rs.close();
			s.close();
		}catch(SQLException e){
			LOG.warn("Failed to execute sql: " + sql, e);
		}
		return indexes;
	}

	private void createIndex(Connection c, String index, String field, String table) {
		notifyIndexesUpdate();
		String sql = "create index " + index + " on " + table + "(" + field + ")";
		try{
			Statement s = c.createStatement();
			s.execute(sql);
			s.close();
		}catch(SQLException e){
			LOG.warn("Failed to execute sql: " + sql, e);
		}
	}

	private void renameIndex(Connection c, String oldIndex, String newIndex, String field, String table) {
		String sql = null;
		notifyIndexesUpdate();
		if(M_sql.getVendor().equals("mysql")) sql = "ALTER TABLE " + table + " DROP INDEX " + oldIndex + ", ADD INDEX " + newIndex + " USING BTREE(" + field + ")";
		else if(M_sql.getVendor().equals("oracle")) sql = "ALTER INDEX " + oldIndex + " RENAME TO " + newIndex;
		try{
			Statement s = c.createStatement();
			s.execute(sql);
			s.close();
		}catch(SQLException e){
			LOG.warn("Failed to execute sql: " + sql, e);
		}
	}
}
