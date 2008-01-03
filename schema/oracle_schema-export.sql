drop table SST_EVENTS cascade constraints;
drop table SST_JOB_RUN cascade constraints;
drop table SST_PREFERENCES cascade constraints;
drop table SST_RESOURCES cascade constraints;
drop table SST_SITEACTIVITY cascade constraints;
drop table SST_SITEVISITS cascade constraints;
drop sequence SST_EVENTS_ID;
drop sequence SST_JOB_RUN_ID;
drop sequence SST_PREFERENCES_ID;
drop sequence SST_RESOURCES_ID;
drop sequence SST_SITEACTIVITY_ID;
drop sequence SST_SITEVISITS_ID;
create table SST_EVENTS (ID number(19,0) not null, USER_ID varchar2(99 char) not null, SITE_ID varchar2(99 char) not null, EVENT_ID varchar2(32 char) not null, EVENT_DATE date not null, EVENT_COUNT number(19,0) not null, primary key (ID));
create table SST_JOB_RUN (ID number(19,0) not null, JOB_START_DATE timestamp, JOB_END_DATE timestamp, START_EVENT_ID number(19,0), END_EVENT_ID number(19,0), LAST_EVENT_DATE timestamp, primary key (ID));
create table SST_PREFERENCES (ID number(19,0) not null, SITE_ID varchar2(99 char) not null unique, PREFS clob not null, primary key (ID));
create table SST_RESOURCES (ID number(19,0) not null, USER_ID varchar2(99 char) not null, SITE_ID varchar2(99 char) not null, RESOURCE_REF varchar2(255 char) not null, RESOURCE_ACTION varchar2(12 char) not null, RESOURCE_DATE date not null, RESOURCE_COUNT number(19,0) not null, primary key (ID));
create table SST_SITEACTIVITY (ID number(19,0) not null, SITE_ID varchar2(99 char) not null, ACTIVITY_DATE date not null, EVENT_ID varchar2(32 char) not null, ACTIVITY_COUNT number(19,0) not null, primary key (ID));
create table SST_SITEVISITS (ID number(19,0) not null, SITE_ID varchar2(99 char) not null, VISITS_DATE date not null, TOTAL_VISITS number(19,0) not null, TOTAL_UNIQUE number(19,0) not null, primary key (ID));
create index SST_EVENTS_SITE_ID_IX on SST_EVENTS (SITE_ID);
create index SST_EVENTS_USER_ID_IX on SST_EVENTS (USER_ID);
create index SST_EVENTS_EVENT_ID_IX on SST_EVENTS (EVENT_ID);
create index SST_EVENTS_DATE_IX on SST_EVENTS (EVENT_DATE);
create index SST_PREFERENCES_SITE_ID_IX on SST_PREFERENCES (SITE_ID);
create index SST_RESOURCES_DATE_IX on SST_RESOURCES (RESOURCE_DATE);
create index SST_RESOURCES_RES_ACT_IDX on SST_RESOURCES (RESOURCE_ACTION);
create index SST_RESOURCES_USER_ID_IX on SST_RESOURCES (USER_ID);
create index SST_RESOURCES_SITE_ID_IX on SST_RESOURCES (SITE_ID);
create index SST_SITEACTIVITY_DATE_IX on SST_SITEACTIVITY (ACTIVITY_DATE);
create index SST_SITEACTIVITY_EVENT_ID_IX on SST_SITEACTIVITY (EVENT_ID);
create index SST_SITEACTIVITY_SITE_ID_IX on SST_SITEACTIVITY (SITE_ID);
create index SST_SITEVISITS_SITE_ID_IX on SST_SITEVISITS (SITE_ID);
create index SST_SITEVISITS_DATE_IX on SST_SITEVISITS (VISITS_DATE);
create sequence SST_EVENTS_ID;
create sequence SST_JOB_RUN_ID;
create sequence SST_PREFERENCES_ID;
create sequence SST_RESOURCES_ID;
create sequence SST_SITEACTIVITY_ID;
create sequence SST_SITEVISITS_ID;