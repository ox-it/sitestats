-- This is the MySQL SiteStats 2.2 -> 2.3 conversion script
----------------------------------------------------------------------------------------------------------------------------------------
--
-- Run this before you run your first app server with the updated SiteStats.
-- auto.ddl does not need to be enabled in your app server - this script takes care of all new TABLEs, changed TABLEs, and changed data.
--
----------------------------------------------------------------------------------------------------------------------------------------


-- STAT-299: Reimplementation of server wide stats
create table SST_SERVERSTATS (ID bigint not null auto_increment, ACTIVITY_DATE date not null, EVENT_ID varchar(32) not null, EVENT_COUNT bigint default 0 not null, primary key (ID));
create index SST_SERVERSTATS_DATE_IX on SST_SERVERSTATS (ACTIVITY_DATE);
create index SST_SERVERSTATS_EVENT_ID_IX on SST_SERVERSTATS (EVENT_ID);