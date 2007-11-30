/**********************************************************************************
 *
 * Copyright (c) 2006 Universidade Fernando Pessoa
 *
 * Licensed under the Educational Community License Version 1.0 (the "License");
 * By obtaining, using and/or copying this Original Work, you agree that you have read,
 * understand, and will comply with the terms and conditions of the Educational Community License.
 * You may obtain a copy of the License at:
 *
 *      http://cvs.sakaiproject.org/licenses/license_1_0.html
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 **********************************************************************************/
package org.sakaiproject.sitestats.impl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Expression;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.sitestats.api.EventParserTip;
import org.sakaiproject.sitestats.api.EventStat;
import org.sakaiproject.sitestats.api.ResourceStat;
import org.sakaiproject.sitestats.api.SiteActivity;
import org.sakaiproject.sitestats.api.SiteVisits;
import org.sakaiproject.sitestats.api.StatsManager;
import org.sakaiproject.sitestats.api.StatsUpdateManager;
import org.sakaiproject.sitestats.api.ToolInfo;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;


/**
 * @author <a href="mailto:nuno@ufp.pt">Nuno Fernandes</a>
 */
public class StatsUpdateManagerImpl extends HibernateDaoSupport implements Runnable, StatsUpdateManager, Observer {
	private Log								LOG									= LogFactory.getLog(StatsUpdateManagerImpl.class);
	private final static String				PRESENCE_SUFFIX						= "-presence";
	private final static int				PRESENCE_SUFFIX_LENGTH				= PRESENCE_SUFFIX.length();

	/** Spring bean members */
	private boolean							collectThreadEnabled				= true;
	public long								collectThreadUpdateInterval			= 4000L;
	private boolean							collectAdminEvents					= false;
	private boolean							collectEventsForSiteWithToolOnly	= true;

	/** Sakai services */
	private StatsManager					M_sm;
	private SiteService						M_ss;
	private UsageSessionService				M_uss;
	private EventTrackingService			M_ets;

	/** Collect Thread and Semaphore */
	private Thread							collectThread;
	private List<Event>						collectThreadQueue					= new ArrayList<Event>();
	private Object							collectThreadSemaphore				= new Object();
	private boolean							collectThreadRunning				= true;

	/** Collect thread queue maps */
	private Map<String, EventStat>			eventStatMap						= Collections.synchronizedMap(new HashMap<String, EventStat>());
	private Map<String, ResourceStat>		resourceStatMap						= Collections.synchronizedMap(new HashMap<String, ResourceStat>());
	private Map<String, SiteActivity>		activityMap							= Collections.synchronizedMap(new HashMap<String, SiteActivity>());
	private Map<String, SiteVisits>			visitsMap							= Collections.synchronizedMap(new HashMap<String, SiteVisits>());
	private Map<UniqueVisitsKey, Integer>	uniqueVisitsMap						= Collections.synchronizedMap(new HashMap<UniqueVisitsKey, Integer>());

	private List<String>					registeredEvents					= null;
	private Map<String, ToolInfo>			eventIdToolMap						= null;


	
	// ################################################################
	// Spring related methods
	// ################################################################	
	public void setCollectThreadEnabled(boolean enabled) {
		this.collectThreadEnabled = enabled;
	}
	
	public boolean isCollectThreadEnabled() {
		return collectThreadEnabled;
	}
	
	public void setCollectThreadUpdateInterval(long dbUpdateInterval){
		this.collectThreadUpdateInterval = dbUpdateInterval;
	}
	
	public long getCollectThreadUpdateInterval(){
		return collectThreadUpdateInterval;
	}	
	
	public void setCollectAdminEvents(boolean value){
		this.collectAdminEvents = value;
	}

	public boolean isCollectAdminEvents(){
		return collectAdminEvents;
	}

	public void setCollectEventsForSiteWithToolOnly(boolean value){
		this.collectEventsForSiteWithToolOnly = value;
	}
	
	public boolean isCollectEventsForSiteWithToolOnly(){
		return collectEventsForSiteWithToolOnly;
	}
	
	public void setStatsManager(StatsManager mng){
		this.M_sm = mng;
	}
	
	public void setSiteService(SiteService ss){
		this.M_ss = ss;
	}
	
	public void setEventTrackingService(EventTrackingService ets){
		this.M_ets = ets;
	}
	
	public void setUsageSessionService(UsageSessionService uss){
		this.M_uss = uss;
	}
	
	public void init(){
		// get all registered events
		registeredEvents = M_sm.getAllToolEventIds();
		// add site visit event
		registeredEvents.add(M_sm.getSiteVisitEventId());
		// get eventId -> ToolInfo map
		eventIdToolMap = M_sm.getEventIdToolMap();
		
		logger.info("init(): - collect thread enabled: " + collectThreadEnabled);
		if(collectThreadEnabled) {
			logger.info("init(): - collect thread db update interval: " + collectThreadUpdateInterval +" ms");
			logger.info("init(): - collect administrator events: " + collectAdminEvents);
			logger.info("init(): - collect events only for sites with SiteStats: " + collectEventsForSiteWithToolOnly);
			
			// start update thread
			startUpdateThread();
			
			// add this as EventInfo observer
			M_ets.addLocalObserver(this);
		}
	}
	
	public void destroy(){
		if(collectThreadEnabled) {
			// remove this as EventInfo observer
			M_ets.deleteObserver(this);	
			
			// stop update thread
			stopUpdateThread();
		}
	}

	
	// ################################################################
	// Public methods
	// ################################################################
//	public static EventInfo buildEvent(String event, String resource, Date date, String contextId, String userId) {
//		return new DetailedEvent();
//	}
	
	/* (non-Javadoc)
	 * @see org.sakaiproject.sitestats.api.StatsUpdateManager#collectEvent(org.sakaiproject.event.api.Event)
	 */
	public synchronized void collectEvent(Event e) {
		String userId = e.getUserId();
		e = fixMalFormedEvents(e);
		if(registeredEvents.contains(e.getEvent()) && isValidEvent(e)){
			
			// site check
			String siteId = parseSiteId(e);
			if(siteId == null || M_ss.isUserSite(siteId) || M_ss.isSpecialSite(siteId)){
				return;
			}
			if(isCollectEventsForSiteWithToolOnly()){
				try {
					if(M_ss.getSite(siteId).getToolForCommonId(StatsManager.SITESTATS_TOOLID) == null)
						return;
				}catch(Exception ex) {
					// not a valid site
					return;
				}
			}
			
			// user check
			if(userId == null) userId = M_uss.getSession(e.getSessionId()).getUserId();
			if(!isCollectAdminEvents() && userId.equals("admin")){
				return;
			}

			// consolidate event
			final Date date = getToday();
			final String eventId = e.getEvent();
			final String resourceRef = e.getResource();
			consolidateEvent(date, eventId, resourceRef, userId, siteId);
		}//else LOG.info("EventInfo ignored:  '"+e.toString()+"' ("+e.toString()+") USER_ID: "+userId);
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.sitestats.api.StatsUpdateManager#collectEvents(java.util.List)
	 */
	public synchronized void collectEvents(List<Event> events) {
		if(events != null) {
			Iterator<Event> iE = events.iterator();
			while(iE.hasNext()) {
				Event e = iE.next();
				collectEvent(e);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.sakaiproject.sitestats.api.StatsUpdateManager#collectEvents(org.sakaiproject.event.api.Event[])
	 */
	public synchronized void collectEvents(Event[] events) {
		for(int i=0; i<events.length; i++)
			collectEvent(events[i]);
	}
	

	// ################################################################
	// Update thread related methods
	// ################################################################	
	/** Method called whenever an new event is generated from EventTrackingService: do not call this method! */
	public void update(Observable obs, Object o) {		
		if(o instanceof Event){
			collectThreadQueue.add((Event) o);
		}
	}
	
	/** Update thread: do not call this method! */
	public void run(){
		try{
			while(collectThreadRunning){
				// do update job
				while(collectThreadQueue.size() > 0){
					collectEvent(collectThreadQueue.remove(0));
				}
				doUpdateConsolidatedEvents();
				
				// sleep if no work to do
				if(!collectThreadRunning) break;
				try{
					synchronized (collectThreadSemaphore){
						collectThreadSemaphore.wait(collectThreadUpdateInterval);
					}
				}catch(InterruptedException e){
					LOG.warn("Failed to sleep statistics update thread",e);
				}
			}
		}catch(Throwable t){
			LOG.debug("Failed to execute statistics update thread",t);
		}finally{
			if(collectThreadRunning){
				// thread was stopped by an unknown error: restart
				LOG.debug("Statistics update thread was stoped by an unknown error: restarting...");
				startUpdateThread();
			}else
				LOG.info("Finished statistics update thread");
		}
	}

	/** Start the update thread */
	private void startUpdateThread(){
		collectThreadRunning = true;
		collectThread = null;
		collectThread = new Thread(this, "org.sakaiproject.sitestats.impl.StatsUpdateManagerImpl");
		collectThread.start();
	}
	
	/** Stop the update thread */
	private void stopUpdateThread(){
		collectThreadRunning = false;
		synchronized (collectThreadSemaphore){
			collectThreadSemaphore.notifyAll();
		}
	}
	

	// ################################################################
	// Update methods
	// ################################################################	
	private void consolidateEvent(Date date, String eventId, String resourceRef, String userId, String siteId) {
		// update		
		if(registeredEvents.contains(eventId)){	
			// add to eventStatMap
			String key = userId+siteId+eventId+date;
			synchronized(eventStatMap){
				EventStat e1 = eventStatMap.get(key);
				if(e1 == null){
					e1 = new EventStatImpl();
					e1.setUserId(userId);
					e1.setSiteId(siteId);
					e1.setEventId(eventId);
					e1.setDate(date);
				}
				e1.setCount(e1.getCount() + 1);
				eventStatMap.put(key, e1);
			}
			
			if(!eventId.equals("pres.begin")){
				// add to activityMap
				String key2 = siteId+date+eventId;
				synchronized(activityMap){
					SiteActivity e2 = activityMap.get(key2);
					if(e2 == null){
						e2 = new SiteActivityImpl();
						e2.setSiteId(siteId);
						e2.setDate(date);
						e2.setEventId(eventId);
					}
					e2.setCount(e2.getCount() + 1);
					activityMap.put(key2, e2);
				}
			}
		}	
		
		if(eventId.startsWith("content.")){
			// add to resourceStatMap
			String resourceAction = null;
			try{
				resourceAction = eventId.split("\\.")[1];
			}catch(ArrayIndexOutOfBoundsException ex){
				resourceAction = eventId;
			}
			String key = userId+siteId+resourceRef+resourceAction+date;
			synchronized(resourceStatMap){
				ResourceStat e1 = resourceStatMap.get(key);
				if(e1 == null){
					e1 = new ResourceStatImpl();
					e1.setUserId(userId);
					e1.setSiteId(siteId);
					e1.setResourceRef(resourceRef);
					e1.setResourceAction(resourceAction);
					e1.setDate(date);
				}
				e1.setCount(e1.getCount() + 1);
				resourceStatMap.put(key, e1);
			}
			
		}else if(eventId.equals("pres.begin")){
			// add to visitsMap
			String key = siteId+date;
			synchronized(visitsMap){
				SiteVisits e1 = visitsMap.get(key);
				if(e1 == null){
					e1 = new SiteVisitsImpl();
					e1.setSiteId(siteId);
					e1.setDate(date);
				}
				e1.setTotalVisits(e1.getTotalVisits() + 1);
				// unique visits are determined when updating to db:
				//   --> e1.setTotalUnique(totalUnique);
				visitsMap.put(key, e1);
			}			
			UniqueVisitsKey keyUniqueVisits = new UniqueVisitsKey(siteId, date);
			// place entry on map so we can update unique visits later
			uniqueVisitsMap.put(keyUniqueVisits, Integer.valueOf(1));
		}
	}	
	
	@SuppressWarnings("unchecked")
	private synchronized void doUpdateConsolidatedEvents() {
		getHibernateTemplate().execute(new HibernateCallback() {			
			public Object doInHibernate(Session session) throws HibernateException, SQLException {
				Transaction tx = null;
				try{
					tx = session.beginTransaction();
					// do: EventStat
					if(eventStatMap.size() > 0) {
						Collection<EventStat> tmp1 = null;
						synchronized(eventStatMap){
							tmp1 = eventStatMap.values();
							eventStatMap = Collections.synchronizedMap(new HashMap<String, EventStat>());
						}
						doUpdateEventStatObjects(session, tmp1);
					}
					
					// do: ResourceStat
					if(resourceStatMap.size() > 0) {
						Collection<ResourceStat> tmp2 = null;
						synchronized(resourceStatMap){
							tmp2 = resourceStatMap.values();
							resourceStatMap = Collections.synchronizedMap(new HashMap<String, ResourceStat>());
						}
						doUpdateResourceStatObjects(session, tmp2);
					}
					
					// do: SiteActivity
					if(activityMap.size() > 0) {
						Collection<SiteActivity> tmp3 = null;
						synchronized(activityMap){
							tmp3 = activityMap.values();
							activityMap = Collections.synchronizedMap(new HashMap<String, SiteActivity>());
						}
						doUpdateSiteActivityObjects(session, tmp3);
					}

					// do: SiteVisits
					if(uniqueVisitsMap.size() > 0 || visitsMap.size() > 0) {	
						// determine unique visits for event related sites
						Map<UniqueVisitsKey, Integer> tmp4;
						synchronized(uniqueVisitsMap){
							tmp4 = uniqueVisitsMap;
							uniqueVisitsMap = Collections.synchronizedMap(new HashMap<UniqueVisitsKey, Integer>());
						}
						tmp4 = doGetSiteUniqueVisits(session, tmp4);
					
						// do: SiteVisits
						if(visitsMap.size() > 0) {
							Collection<SiteVisits> tmp5 = null;
							synchronized(visitsMap){
								tmp5 = visitsMap.values();
								visitsMap = Collections.synchronizedMap(new HashMap<String, SiteVisits>());
							}
							doUpdateSiteVisitsObjects(session, tmp5, tmp4);
						}
					}

					// commit ALL
					tx.commit();
				}catch(Exception e){
					if(tx != null) tx.rollback();
					LOG.warn("Unable to commit transaction: ", e);
				}
				return null;
			}			
		});
	}
	
	private void doUpdateEventStatObjects(Session session, Collection<EventStat> objects) {
		if(objects == null) return;
		Iterator<EventStat> i = objects.iterator();
		while(i.hasNext()){
			EventStat eUpdate = i.next();
			Criteria c = session.createCriteria(EventStatImpl.class);
			c.add(Expression.eq("siteId", eUpdate.getSiteId()));
			c.add(Expression.eq("eventId", eUpdate.getEventId()));
			c.add(Expression.eq("userId", eUpdate.getUserId()));
			c.add(Expression.eq("date", eUpdate.getDate()));
			EventStat eExisting = null;
			try{
				eExisting = (EventStat) c.uniqueResult();
			}catch(Exception ex){
				LOG.debug("More than 1 result when unique result expected.", ex);
				eExisting = (EventStat) c.list().get(0);
			}
			if(eExisting == null) 
				eExisting = eUpdate;
			else
				eExisting.setCount(eExisting.getCount() + eUpdate.getCount());

			session.saveOrUpdate(eExisting);
		}
	}

	private void doUpdateResourceStatObjects(Session session, Collection<ResourceStat> objects) {
		if(objects == null) return;
		Iterator<ResourceStat> i = objects.iterator();
		while(i.hasNext()){
			ResourceStat eUpdate = i.next();
			Criteria c = session.createCriteria(ResourceStatImpl.class);
			c.add(Expression.eq("siteId", eUpdate.getSiteId()));
			c.add(Expression.eq("resourceRef", eUpdate.getResourceRef()));
			c.add(Expression.eq("resourceAction", eUpdate.getResourceAction()));
			c.add(Expression.eq("userId", eUpdate.getUserId()));
			c.add(Expression.eq("date", eUpdate.getDate()));
			ResourceStat eExisting = null;
			try{
				eExisting = (ResourceStat) c.uniqueResult();
			}catch(Exception ex){
				LOG.debug("More than 1 result when unique result expected.", ex);
				eExisting = (ResourceStat) c.list().get(0);
			}
			if(eExisting == null) 
				eExisting = eUpdate;
			else
				eExisting.setCount(eExisting.getCount() + eUpdate.getCount());

			session.saveOrUpdate(eExisting);
		}
	}
	
	private void doUpdateSiteActivityObjects(Session session, Collection<SiteActivity> objects) {
		if(objects == null) return;
		Iterator<SiteActivity> i = objects.iterator();
		while(i.hasNext()){
			SiteActivity eUpdate = i.next();
			Criteria c = session.createCriteria(SiteActivityImpl.class);
			c.add(Expression.eq("siteId", eUpdate.getSiteId()));
			c.add(Expression.eq("eventId", eUpdate.getEventId()));
			c.add(Expression.eq("date", eUpdate.getDate()));
			SiteActivity eExisting = null;
			try{
				eExisting = (SiteActivity) c.uniqueResult();
			}catch(Exception ex){
				LOG.debug("More than 1 result when unique result expected.", ex);
				eExisting = (SiteActivity) c.list().get(0);
			}
			if(eExisting == null) 
				eExisting = eUpdate;
			else
				eExisting.setCount(eExisting.getCount() + eUpdate.getCount());

			session.saveOrUpdate(eExisting);
		}
	}
	
	private void doUpdateSiteVisitsObjects(Session session, Collection<SiteVisits> objects, Map<UniqueVisitsKey, Integer> map) {
		if(objects == null) return;
		Iterator<SiteVisits> i = objects.iterator();
		while(i.hasNext()){
			SiteVisits eUpdate = i.next();
			Criteria c = session.createCriteria(SiteVisitsImpl.class);
			c.add(Expression.eq("siteId", eUpdate.getSiteId()));
			c.add(Expression.eq("date", eUpdate.getDate()));
			SiteVisits eExisting = null;
			try{
				eExisting = (SiteVisits) c.uniqueResult();
			}catch(Exception ex){
				LOG.debug("More than 1 result when unique result expected.", ex);
				eExisting = (SiteVisits) c.list().get(0);
			}
			if(eExisting == null){
				eExisting = eUpdate;
			}else{
				eExisting.setTotalVisits(eExisting.getTotalVisits() + eUpdate.getTotalVisits());
			}
			Integer mapUV = map.get(new UniqueVisitsKey(eExisting.getSiteId(), eExisting.getDate()));
			eExisting.setTotalUnique(mapUV == null? 1 : mapUV.longValue());

			session.saveOrUpdate(eExisting);
		}
	}
	
	private Map<UniqueVisitsKey, Integer> doGetSiteUniqueVisits(Session session, Map<UniqueVisitsKey, Integer> map) {
		Iterator<UniqueVisitsKey> i = map.keySet().iterator();
		while(i.hasNext()){
			UniqueVisitsKey key = i.next();
			Query q = session.createQuery("select count(distinct s.userId) " + 
					"from EventStatImpl as s " +
					"where s.siteId = :siteid " +
					"and s.eventId = 'pres.begin' " +
					"and s.date = :idate");
			q.setString("siteid", key.siteId);
			q.setDate("idate", key.date);
			Integer uv = 1;
			try{
				uv = (Integer) q.uniqueResult();
			}catch(Exception ex){
				LOG.debug("More than 1 result when unique result expected.", ex);
				uv = (Integer) q.list().get(0);
			}
			int uniqueVisits = uv == null? 1 : uv.intValue();
			map.put(key, Integer.valueOf((int)uniqueVisits));			
		}
		return map;
	}
	

	// ################################################################
	// Utility methods
	// ################################################################	
	private synchronized boolean isValidEvent(Event e) {
		if(e.getEvent().startsWith("content")){
			String ref = e.getResource();	
			if(ref.trim().equals("")) return false;			
			try{
				String parts[] = ref.split("\\/");		
				if(parts[2].equals("user")){
					// workspace (ignore)
					return false;
				}else if(parts[2].equals("attachment") && parts.length < 6){
					// ignore mail attachments (no reference to site)
					return false;
				}else if(parts[2].equals("group")){
					// resources
					if(parts.length <= 4) return false;	
				}else if(parts[2].equals("group-user")){
					// drop-box
					if(parts.length <= 5) return false;
				}
			}catch(Exception ex){
				return false;
			}
		}
		return true;
	}
	
	private Event fixMalFormedEvents(Event e){
		String event = e.getEvent();
		String resource = e.getResource();
		
		// OBSOLETE: fix bad reference (resource) format
		// => Use <eventParserTip> instead
			//if(!resource.startsWith("/"))
			//	resource = '/' + resource;
		
		// MessageCenter (OLD) CASE: Handle old MessageCenter events */
		if(event.startsWith("content.") && resource.startsWith("MessageCenter")) {
			resource = resource.replaceFirst("MessageCenter::", "/MessageCenter/site/");
			resource = resource.replaceAll("::", "/");
			return M_ets.newEvent(
					event.replaceFirst("content.", "msgcntr."), 
					resource, 
					false);
		}

		return e;
	}
	
	private String parseSiteId(Event e){
		String eventId = e.getEvent();
		String eventRef = e.getResource();
		
		try{
			if(eventId.equals(StatsManager.SITEVISIT_EVENTID)) {
				
				// presence (site visit) syntax (/presence/SITE_ID-presence)
				String[] parts = eventRef.split("/");
				if(parts[2].endsWith(PRESENCE_SUFFIX))
					return parts[2].substring(0, parts[2].length() - PRESENCE_SUFFIX_LENGTH);
				else
					return null;	
				
			}else {

				// use <eventParserTip>
				ToolInfo toolInfo = eventIdToolMap.get(eventId);
				EventParserTip parserTip = toolInfo.getEventParserTip();
				if(parserTip != null && parserTip.getFor().equals(StatsManager.PARSERTIP_FOR_CONTEXTID)) {
					int index = Integer.parseInt(parserTip.getIndex());
					return eventRef.split(parserTip.getSeparator())[index];
					
				}else {
					// try with most common syntax (/abc/cde/SITE_ID/...)
					return eventRef.split("/")[3];
				}
			}
		}catch(Exception ex){
			LOG.warn("Unable to parse contextId from event: " + eventId + " | " + eventRef, ex);
		}
		return null;
	}
	
	/*
	private String parseSiteId_Old(String ref){
		try{
			String[] parts = ref.split("/");
			if(parts == null)
				return null;
			if(parts.length == 1){
				// try with OLD MessageCenter syntax (MessageCenter::SITE_ID::...)
				parts = ref.split("::");
				return parts.length > 1 ? parts[1] : null;
			}
			if(parts[0].equals("MessageCenter")){
				// MessageCenter without initial '/'
				return parts[2];
			}
			if(parts[0].equals("")){
				if(parts[1].equals("presence"))
					// try with presence syntax (/presence/SITE_ID-presence)
					if(parts[2].endsWith("-presence"))
						return parts[2].substring(0,parts[2].length()-9);
					else
						return null;
				else if(parts[1].equals("syllabus"))
					// try with Syllabus syntax (/syllabus/SITE_ID/...)
					return parts[2];
				else if(parts[1].equals("site"))
					// try with Section Info syntax (/site/SITE_ID/...)
					return parts[2];
				else if(parts[1].equals("gradebook"))
					// try with Gradebook syntax (/gradebook/SITE_ID/...)
					return parts[2];
				else if(parts[1].equals("tasklist") || parts[1].equals("todolist"))
					// try with Tasklist/TodoList syntax (/tasklist/SITE_ID/...)
					return parts[2];
				else
					// try with most common syntax (/abc/cde/SITE_ID/...)
					return parts[3];
			}
		}catch(Exception e){
			LOG.debug("Unable to parse site ID from "+ref, e);
		}
		return null;
	}
	*/
	
	private Date getToday() {
		Calendar c = Calendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		return c.getTime();
	}
	
	private class UniqueVisitsKey {
		public String siteId;
		public Date date;
		
		public UniqueVisitsKey(String siteId, Date date){
			this.siteId = siteId;
			this.date = resetToDay(date);
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof UniqueVisitsKey) {
				UniqueVisitsKey u = (UniqueVisitsKey) o;
				return siteId.equals(u.siteId) && date.equals(u.date);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return siteId.hashCode() + date.hashCode();
		}
		
		private Date resetToDay(Date date){
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			return c.getTime();
		}
	}
}
