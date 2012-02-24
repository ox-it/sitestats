/**
 * $URL$
 * $Id$
 *
 * Copyright (c) 2006-2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.sitestats.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.StatefulJob;
import org.sakaiproject.component.app.scheduler.jobs.SpringJobBeanWrapper;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.sitestats.api.JobRun;
import org.sakaiproject.sitestats.api.StatsUpdateManager;


public class StatsAggregateJobImpl implements StatefulJob {
	private Log					LOG					= LogFactory.getLog(StatsAggregateJobImpl.class);

	// Spring fields
	private int					maxEventsPerRun		= 0;
	private int					sqlBlockSize		= 1000;
	private long				startEventId		= -1;
	private long 				lastEventIdInTable	= -1;

	private String				driverClassName		= null;
	private String				url					= null;
	private String				username			= null;
	private String				password			= null;
	
	// Relevant job fields
	private JobRun				jobRun				= null;
	private Object				extDbdriver			= null;
	private String				sqlGetEvent			= null;
	private String				sqlPastSiteEvents	= null;
	private boolean				isOracle 			= false;
	private boolean				isEventContextSupported = false;

	private final static String LAST_EVENT_ID		= "select max(EVENT_ID) LAST_ID from SAKAI_EVENT";
	private final static String MYSQL_DEFAULT_COLUMNS  = "EVENT_ID as EVENT_ID,EVENT_DATE as EVENT_DATE,EVENT as EVENT,REF as REF,SESSION_USER as SESSION_USER,e.SESSION_ID as SESSION_ID";
	private final static String ORACLE_DEFAULT_COLUMNS = "EVENT_ID,EVENT_DATE,EVENT,REF,SESSION_USER,e.SESSION_ID SESSION_ID";
	private final static String MYSQL_CHECK_FOR_CONTEXT = "show columns from SAKAI_EVENT like 'CONTEXT'";
	private final static String ORACLE_CHECK_FOR_CONTEXT = "select column_name from USER_TAB_COLUMNS where table_name='SAKAI_EVENT' and column_name='CONTEXT'";
	private final static String MYSQL_CONTEXT_COLUMN   = ",CONTEXT as CONTEXT";
	private final static String ORACLE_CONTEXT_COLUMN  = ",CONTEXT";
	private String MYSQL_GET_EVENT					= "select " + MYSQL_DEFAULT_COLUMNS + MYSQL_CONTEXT_COLUMN + " " +
														"from SAKAI_EVENT e join SAKAI_SESSION s on e.SESSION_ID=s.SESSION_ID " +
														"where EVENT_ID >= ? and EVENT_ID < ? " +
														"order by EVENT_ID asc ";
	private String ORACLE_GET_EVENT					= "SELECT * FROM ( " +
														"SELECT " +
															" ROW_NUMBER() OVER (ORDER BY EVENT_ID ASC) AS rn, " +
															ORACLE_DEFAULT_COLUMNS + ORACLE_CONTEXT_COLUMN + " " +
														"from SAKAI_EVENT e join SAKAI_SESSION s on e.SESSION_ID=s.SESSION_ID " +
														"where EVENT_ID >= ? " +
														") " +
														"WHERE rn BETWEEN ? AND  ?";
	private String MYSQL_PAST_SITE_EVENTS			= "select " + MYSQL_DEFAULT_COLUMNS + MYSQL_CONTEXT_COLUMN + " " +
														"from SAKAI_EVENT e join SAKAI_SESSION s on e.SESSION_ID=s.SESSION_ID " +
														"where (CONTEXT = ? or (EVENT in ('pres.begin','pres.end') and REF = ?)) " +
														"and EVENT_DATE >= ? and EVENT_DATE <= ?";
	private String ORACLE_PAST_SITE_EVENTS			= "SELECT " + ORACLE_DEFAULT_COLUMNS + ORACLE_CONTEXT_COLUMN + " " +
														"from SAKAI_EVENT e join SAKAI_SESSION s on e.SESSION_ID=s.SESSION_ID " +
														"where (CONTEXT = ? or (EVENT in ('pres.begin','pres.end') and REF = ?)) " +
														"and EVENT_DATE >= ? and EVENT_DATE <= ?";
	
	// Services
	private StatsUpdateManager	statsUpdateManager	= null;
	private SqlService			sqlService			= null;

	public void init(){
		doInitialCheck();
		LOG.info("StatsAggregateJobImpl.init()");		
	}
	
	private void doInitialCheck() {
		JobRun lastJobRun = null;
		try{
			lastJobRun = getLastJobRun();
		}catch(Exception e){
			LOG.error("Make sure SST_JOB_RUN table is created before running the StatsAggregateJob job.");
		}
		if(lastJobRun == null && !statsUpdateManager.isCollectThreadEnabled()){
			if(getStartEventId() < 0){
				long lastEventIdInTable = 0;
				try{
					lastEventIdInTable = getLastEventIdInTable();
				}catch(SQLException e){
					LOG.warn("Unable to check last eventId in table SAKAI_EVENT --> assuming 0.", e);
				}
				LOG.warn("First StatsAggregateJob job run will use last SAKAI_EVENT.EVENT_ID (id = "+lastEventIdInTable+"). To override this, please specify a new eventId in sakai.properties (property: startEventId@org.sakaiproject.sitestats.api.StatsAggregateJob=n, where n>=0). This value is for the first job run only.");
			}else{
				LOG.warn("First StatsAggregateJob job run will use 'startEventId' ("+getStartEventId()+") specified in sakai.properties. This value is for the first job run only.");
			}
		}
		
	}
	
	// ################################################################
	// Job related methods
	// ################################################################
	public void execute(JobExecutionContext context) throws JobExecutionException {
		String result = null;
		String jobName = context.getJobDetail().getFullName();
		
		// ABORT if job is currently running in this cluster node.
		//  -> Required as StatefullJob is only correctly supported in trunk!
		// WARNING: I cannot currently check if this is running in OTHER cluster nodes!!!
		try{
			while(isJobCurrentlyRunning(context)) {
				String beanId = context.getJobDetail().getJobDataMap().getString(SpringJobBeanWrapper.SPRING_BEAN_NAME);
				LOG.warn("Another instance of "+beanId+" is currently running - Execution aborted.");
				return;
			}
		}catch(SchedulerException e){
			LOG.error("Aborting job execution due to "+e.toString(), e);
			return;
		}
		
		LOG.info("Starting job: " + jobName);
		
		// check for SAKAI_EVENT.CONTEXT column
		try{
			checkForContextColumn();
			LOG.debug("SAKAI_EVENT.CONTEXT exists? "+isEventContextSupported);
		}catch(SQLException e1){
			LOG.warn("Unable to check existence of SAKAI_EVENT.CONTEXT", e1);
		}

		// configure job
		JobRun lastJobRun;
		try{
			lastJobRun = getLastJobRun();
		}catch(Exception e){
			LOG.error("Error accessing SST_JOB_RUN table. Does this table exists? Aborting job...");
			return;
		}
		jobRun = new JobRunImpl();
		jobRun.setJobStartDate(new Date(System.currentTimeMillis()));
		if(lastJobRun != null){
			jobRun.setStartEventId(lastJobRun.getEndEventId() + 1);
		}else if(getStartEventId() >= 0){
			LOG.info("First job run: using 'startEventId' ("+getStartEventId()+") specified in sakai.properties. This value is for the first job run only.");
			jobRun.setStartEventId(getStartEventId());
		}else{
			long lastEventIdInTable = 0;
			try{
				lastEventIdInTable = getLastEventIdInTable();
			}catch(SQLException e){
				LOG.warn("Unable to check last eventId in table SAKAI_EVENT --> assuming 0.", e);
			}
			LOG.info("First job run: no 'startEventId' specified in sakai.properties; using last SAKAI_EVENT.EVENT_ID (id = "+lastEventIdInTable+"). This value is for the first job run only.");
			jobRun.setStartEventId(lastEventIdInTable);
		}

		// start job
		try{
			result = startJob();
			LOG.info("Summary: " + result);
		}catch(SQLException e){
			LOG.error("Summary: job run failed", e);
		}

		// finish		
		LOG.info("Finishing job: " + jobName);
	}

	private boolean isJobCurrentlyRunning(JobExecutionContext context) throws SchedulerException {
		String beanId = context.getJobDetail().getJobDataMap().getString(SpringJobBeanWrapper.SPRING_BEAN_NAME);
		List<JobExecutionContext> jobsRunning = context.getScheduler().getCurrentlyExecutingJobs();
		
		int jobsCount = 0;
		for(JobExecutionContext j : jobsRunning)
			if(beanId.equals(j.getJobDetail().getJobDataMap().getString(SpringJobBeanWrapper.SPRING_BEAN_NAME))) {
				jobsCount++;
			}
		if(jobsCount > 1)
			return true;
		return false;
	}

	private long getLastEventIdInTable() throws SQLException {
		if(lastEventIdInTable == -1) {
			Connection connection = null;
			Statement st = null;
			ResultSet rs = null;
			try{
				connection = getEventDbConnection();
				st = connection.createStatement();
				rs = st.executeQuery(LAST_EVENT_ID);
				if(rs.next()){
					lastEventIdInTable = rs.getLong("LAST_ID");
				}
			}catch(SQLException e){
				LOG.error("Unable to retrieve events", e); 
			}finally{
				try{
					if(rs != null)
						rs.close();
				}finally{
					try{
						if(st != null)
							st.close();
					}finally{
						closeEventDbConnection(connection);
					}
				}
			}
		}
		return lastEventIdInTable;
	}

	private String startJob() throws SQLException {
		List<Event> eventsQueue = new ArrayList<Event>();
		long counter = 0;
		long offset = 0;
		long lastProcessedEventId = 0;
		long lastProcessedEventIdWithSuccess = 0;
		long firstEventIdProcessed = -1;
		long firstEventIdProcessedInBlock = -1;
		Date lastEventDate = null;
		Date lastEventDateWithSuccess = null;
		boolean abortIteration = false;
		long start = System.currentTimeMillis();
		boolean sqlError = false;
		String returnMessage = null;
		
		Connection connection = getEventDbConnection();
		long eventIdLowerLimit = getEventIdLowerLimit();
		PreparedStatement st = null;
		ResultSet rs = null;
		try{
			st = connection.prepareStatement(sqlGetEvent);
			rs = null;
			
			while(!abortIteration) {
				abortIteration = true;
				st.clearParameters();		
				if(!isOracle){
					if(firstEventIdProcessed == -1)
						offset = eventIdLowerLimit;
					st.setLong(1, offset);					// MySQL >= startId	
					st.setLong(2, sqlBlockSize + offset);	// MySQL < endId
				}else{
					st.setLong(1, eventIdLowerLimit);		// Oracle lower limit	
					st.setLong(2, offset);					// Oracle offset
					st.setLong(3, sqlBlockSize + offset);	// Oracle limit	
				}
				rs = st.executeQuery();
				
				while(rs.next()){
					abortIteration = false;
					Date date = null;
					String event = null;
					String ref = null;
					String context = null;
					String sessionUser = null;
					String sessionId = null;
					try{
						//If an exception is launched, iteration is not aborted but no event is added to event queue
						date = new Date(rs.getTimestamp("EVENT_DATE").getTime());
						event = rs.getString("EVENT");
						ref = rs.getString("REF");
						sessionUser = rs.getString("SESSION_USER");
						sessionId = rs.getString("SESSION_ID");
						if(isEventContextSupported)
							context = rs.getString("CONTEXT");
						eventsQueue.add( statsUpdateManager.buildEvent(date, event, ref, context, sessionUser, sessionId) );
						
						counter++;					
						lastProcessedEventId = rs.getInt("EVENT_ID");
						lastEventDate = date;
						if(firstEventIdProcessed == -1)
							firstEventIdProcessed = jobRun.getStartEventId(); //was: lastProcessedEventId;
						if(firstEventIdProcessedInBlock == -1)
							firstEventIdProcessedInBlock = lastProcessedEventId;
					}catch(Exception e){
						if(LOG.isDebugEnabled())
							LOG.debug("Ignoring "+event+", "+ref+", "+date+", "+sessionUser+", "+sessionId+" due to: "+e.toString());
					}
				}
				rs.close();
				
				if(!abortIteration){
					// process events
					boolean processedOk = statsUpdateManager.collectEvents(eventsQueue);
					eventsQueue.clear();
					if(processedOk){
						lastProcessedEventIdWithSuccess = lastProcessedEventId;
						lastEventDateWithSuccess = lastEventDate;
						jobRun.setStartEventId(firstEventIdProcessed);
						jobRun.setEndEventId(lastProcessedEventIdWithSuccess);
						jobRun.setLastEventDate(lastEventDateWithSuccess);
						jobRun.setJobEndDate(new Date(System.currentTimeMillis()));
						saveJobRun(jobRun);
						firstEventIdProcessedInBlock = -1;
						if(counter >= getMaxEventsPerRun()){
							abortIteration = true;
						}else if(counter + sqlBlockSize < getMaxEventsPerRun()){
							offset += sqlBlockSize;	
						}else{
							offset += getMaxEventsPerRun() - counter;
						}
					}else{
						returnMessage = "An error occurred while processing/persisting events to db. Please check your logs, fix possible problems and re-run this job (will start after last successful processed event).";
						LOG.error(returnMessage);
						throw new Exception(returnMessage);
					}
				}
			}

		}catch(SQLException e){
			sqlError = true;
			if(returnMessage == null) {
				returnMessage = "Unable to retrieve events due to: " + e.getMessage();
				LOG.error("Unable to retrieve events", e);
			}
		}catch(Exception e){
			sqlError = true;
			if(returnMessage == null) {
				returnMessage = "Unable to retrieve events due to: " + e.getMessage(); 
				LOG.error("Unable to retrieve events due to an unknown cause", e);
			}
		}finally{
			try{
				if(rs != null)
					rs.close();
			}finally{
				try{
					if(st != null)
						st.close();
				}finally{
					closeEventDbConnection(connection);
				}
			}
		}
		
		// error occurred
		if(sqlError) {
			return returnMessage; 
		}
		
		long processingTime = (System.currentTimeMillis() - start) / 1000;
		
		if(firstEventIdProcessed == -1 && jobRun != null){
			// no data was processed: do not persist to DB
//			long eventId = jobRun.getEndEventId();
//			firstEventIdProcessed = eventId;
//			lastProcessedEventIdWithSuccess = eventId;
//			jobRun.setEndEventId(lastProcessedEventId > 0 ? lastProcessedEventId : jobRun.getStartEventId());
//			jobRun.setLastEventDate(lastEventDate != null ? lastEventDate : null);
//			jobRun.setJobEndDate(new Date(System.currentTimeMillis()));
			return "0 events processed in "+processingTime+"s (no entry will be added to SST_JOB_RUN; only events associated with a session are processed)";
		}else{
			saveJobRun(jobRun);
		}
		
		return counter + " events processed (ids: "+firstEventIdProcessed+" - "+lastProcessedEventIdWithSuccess+") in "+processingTime+"s (only events associated with a session are processed)";
	}

	private long getEventIdLowerLimit() {
		long start = getStartEventId();
		long nextEventId = jobRun.getStartEventId();
		if(nextEventId > start)
			start = nextEventId;
		return start;
	}
	
	private JobRun getLastJobRun() throws Exception {
		return statsUpdateManager.getLatestJobRun();
	}
	
	private boolean saveJobRun(JobRun jobRun) {
		boolean ok = false;
		try{
			ok = statsUpdateManager.saveJobRun(jobRun);
		}catch(Exception e){
			LOG.error("Unable to persist last job information to db.", e);
		}
		return ok;
	}
	
	public long collectPastSiteEvents(String siteId, Date initialDate, Date finalDate) {
		List<Event> eventsQueue = new ArrayList<Event>();
		Connection connection = getEventDbConnection();
		PreparedStatement st = null;
		ResultSet rs = null;
		long count = 0;
		long opStart = System.currentTimeMillis();
		try{
			st = connection.prepareStatement(sqlPastSiteEvents);
			st.setString(1, siteId);									// CONTEXT = ?	
			st.setString(2, "/presence/"+siteId+"-presence");			// REF = ?	
			st.setDate(3, new java.sql.Date(initialDate.getTime()));	// EVENT_DATE >= ?
			st.setDate(4, new java.sql.Date(finalDate.getTime()));		// EVENT_DATE <= ?
			rs = st.executeQuery();
				
			while(rs.next()){
				Date date = null;
				String event = null;
				String ref = null;
				String context = null;
				String sessionUser = null;
				String sessionId = null;
				try{
					//If an exception is launched, iteration is not aborted but no event is added to event queue
					date = new Date(rs.getTimestamp("EVENT_DATE").getTime());
					event = rs.getString("EVENT");
					ref = rs.getString("REF");
					sessionUser = rs.getString("SESSION_USER");
					sessionId = rs.getString("SESSION_ID");
					context = rs.getString("CONTEXT");
					
					eventsQueue.add( statsUpdateManager.buildEvent(date, event, ref, context, sessionUser, sessionId) );					
					count++;				
				}catch(Exception e){
					if(LOG.isDebugEnabled())
						LOG.debug("Ignoring "+event+", "+ref+", "+date+", "+sessionUser+", "+sessionId+" due to: "+e.toString());
				}
			}
			
			// process events
			boolean processedOk = statsUpdateManager.collectEvents(eventsQueue);
			eventsQueue.clear();
			if(!processedOk){
				String returnMessage = "An error occurred while processing/persisting events to db - please check your logs.";
				LOG.error(returnMessage);
				throw new Exception(returnMessage);
			}
			
		}catch(SQLException e){
			LOG.error("Unable to collect past site events", e);
		}catch(Exception e){
			LOG.error("Unable to collect past site due to an unknown cause", e);
		}finally{
			try{
				if(rs != null)
					try{
						rs.close();
					}catch(SQLException e){ }
			}finally{
				try{
					if(st != null)
						try{
							st.close();
						}catch(SQLException e){ }
				}finally{
					closeEventDbConnection(connection);
				}
			}
		}
		
		long opEnd = System.currentTimeMillis();
		LOG.info("Collected "+count+" past events for site "+siteId+" in "+(opEnd-opStart)/1000+" seconds.");
		return count;
	}


	// ################################################################
	// Util methods
	// ################################################################
	private Connection getEventDbConnection() {
		Connection connection = null;
		if(getUrl() == null){
			// SAKAI_EVENT and SAKAI_SESSION are on the same database
			try{
				connection = sqlService.borrowConnection();
				if(sqlService.getVendor().equals("oracle")){
					isOracle = true;
					if(isEventContextSupported)
						sqlGetEvent = ORACLE_GET_EVENT;
					else
						sqlGetEvent = ORACLE_GET_EVENT.replaceAll(ORACLE_CONTEXT_COLUMN, "");
					
					// this feature is only available for Sakai >= 2.6.x
					sqlPastSiteEvents = ORACLE_PAST_SITE_EVENTS;
					
				}else{
					isOracle = false;
					if(isEventContextSupported)
						sqlGetEvent = MYSQL_GET_EVENT;
					else
						sqlGetEvent = MYSQL_GET_EVENT.replaceAll(MYSQL_CONTEXT_COLUMN, "");
					
					// this feature is only available for Sakai >= 2.6.x
					sqlPastSiteEvents = MYSQL_PAST_SITE_EVENTS;
				}
			}catch(SQLException e){
				LOG.error("Unable to connect Sakai Db", e);
				return null;
			}catch(Exception e){
				LOG.error("Unable to connect to Sakai Db", e);
				return null;
			}
		}else{
			// SAKAI_EVENT and SAKAI_SESSION are on different database
			try{
				if(extDbdriver == null){
					extDbdriver = Class.forName(getDriverClassName()).newInstance();
					if(getDriverClassName().equals("oracle.jdbc.driver.OracleDriver")){
						isOracle = true;
						if(isEventContextSupported)
							sqlGetEvent = ORACLE_GET_EVENT;
						else
							sqlGetEvent = ORACLE_GET_EVENT.replaceAll(ORACLE_CONTEXT_COLUMN, "");
					}else{
						isOracle = false;
						if(isEventContextSupported)
							sqlGetEvent = MYSQL_GET_EVENT;
						else
							sqlGetEvent = MYSQL_GET_EVENT.replaceAll(MYSQL_CONTEXT_COLUMN, "");
					}
				}
				connection = DriverManager.getConnection(getUrl(), getUsername(), getPassword());
			}catch(SQLException e){
				LOG.error("Unable to connect to " + getUrl(), e);
				return null;
			}catch(Exception e){
				LOG.error("Unable to connect to " + getUrl(), e);
				return null;
			}
		}
		return connection;
	}

	private void closeEventDbConnection(Connection connection) {
		if(getUrl() == null){
			if(connection != null){
				sqlService.returnConnection(connection);
			}
		}else{
			try{
				if(connection != null && !connection.isClosed()){
					connection.close();
				}
			}catch(SQLException e){
				LOG.error("Unable to close connection " + getUrl(), e);
			}
		}
	}

	private void checkForContextColumn() throws SQLException {
		// Check for SAKAI_EVENT.CONTEXT table column
		Connection connection = null;
		PreparedStatement st = null;
		ResultSet rs = null;
		String sqlCheckForContext = null;
		try{
			connection = getEventDbConnection();
			if(isOracle)
				sqlCheckForContext = ORACLE_CHECK_FOR_CONTEXT;
			else
				sqlCheckForContext = MYSQL_CHECK_FOR_CONTEXT;
			st = connection.prepareStatement(sqlCheckForContext);
			rs = st.executeQuery();			
			if(rs.next()){
				LOG.debug("SAKAI_EVENT.CONTEXT IS present.");
				isEventContextSupported = true;
			} else {
				LOG.debug("SAKAI_EVENT.CONTEXT is NOT present.");
				isEventContextSupported = false;
			}
		}catch(SQLException e){
			LOG.error("Unable to determine if SAKAI_EVENT.CONTEXT is present", e);
			isEventContextSupported = false;
		}finally{
			try{
				if(rs != null)
					rs.close();
			}finally{
				try{
					if(st != null)
						st.close();
				}finally{
					closeEventDbConnection(connection);
				}
			}
		}		
	}

	// ################################################################
	// Spring related methods
	// ################################################################
	public int getMaxEventsPerRun() {
		return maxEventsPerRun;
	}

	public void setMaxEventsPerRun(int maxEventsPerRun) {
		this.maxEventsPerRun = maxEventsPerRun;
	}

	public int getSqlBlockSize() {
		return sqlBlockSize;
	}

	public void setSqlBlockSize(int sqlBlockSize) {
		this.sqlBlockSize = sqlBlockSize;
	}

	public long getStartEventId() {
		return startEventId;
	}

	public void setStartEventId(long startEventId) {
		this.startEventId = startEventId;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public StatsUpdateManager getStatsUpdateManager() {
		return statsUpdateManager;
	}

	public void setStatsUpdateManager(StatsUpdateManager statsUpdateManager) {
		this.statsUpdateManager = statsUpdateManager;
	}

	public SqlService getSqlService() {
		return sqlService;
	}

	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}

}
