/*
 * Copyright (c) 2008-2021, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.mit.ll.nics.processor.collab.feed;


import edu.mit.ll.nics.common.entity.CollabRoom;
import edu.mit.ll.nics.common.entity.HibernateFeature;
import edu.mit.ll.nics.common.entity.Incident;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Extends TimerTask. Synchronizes the mapserver with the DB. If any rooms or incidents stored in the DB do not have
 * corresponding layers on the mapserver, the layers are created.  Also maintains the multi incident view KML document.
 *
 * @author LE22005
 */
public class CollabFeedSync extends TimerTask implements Processor {

    private static final Logger log = Logger.getLogger(CollabFeedSync.class.getSimpleName());
    //Database Entity Manager
    private EntityManagerFactory emf;
    //Geoserver API instance
    private CollabFeedGeoserver geoserver;

    private String log4jPropertyFile;

    //Get DB Connection parameters
    private String dbuser;
    private String dbpass;
    private String dbname;
    private String dbhost;
    private String dbport;

    //Build geoserver connection
    private String geoserverUrl;
    private String geoserverUsername;
    private String geoserverPassword;
    //Get workspace/datastore info
    private String workspaceName;
    private String dataStoreName;

    //List of collabrooms
    private static List<String> geoserverList; //geoserver
    //private static List<String> dbList; //database


    /**
     * Initialize the Synchronization timer
     */
    public CollabFeedSync() {
    }

    public void init() throws Exception {
        log.info("log4jPropertyFile: " + log4jPropertyFile);
        PropertyConfigurator.configure(log4jPropertyFile);

        //setup geoserver connection
        log.info("geoserverUrl: " + geoserverUrl);
        this.geoserver = new CollabFeedGeoserver(geoserverUrl, geoserverUsername, geoserverPassword,
                workspaceName, dataStoreName);

        log.info("Connected to geoserver");

        //setup db connection
        String dbUrl = "jdbc:postgresql://" + dbhost + ":" + dbport + "/" + dbname;
        Map<String, Object> config1 = new HashMap<String, Object>();
        config1.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        config1.put("hibernate.connection.username", dbuser);
        config1.put("hibernate.connection.password", dbpass);
        config1.put("hibernate.connection.url", dbUrl);
        config1.put("hibernate.dialect", "org.hibernate.spatial.dialect.postgis.PostgisDialect");
        this.emf = Persistence.createEntityManagerFactory("manager1", config1);
    }

    /**
     * Process a create incident/room management message
     */
    @Override
    public void process(Exchange e) {
        String message = e.getIn().getBody(String.class);
        String topic = (String) e.getIn().getHeader(RabbitMQConstants.ROUTING_KEY);

        log.debug("Recieved message: " + message);
        try {
            //log.info("Recieved message: " + message);
            //get the JSON message from the exchange
            JSONObject msg = new JSONObject(message);

            if(topic.indexOf("feature") != -1) { //feature message
                log.info("processing feature message");

                Pattern p = Pattern.compile("collabroom.([0-9]*)");
                Matcher m = p.matcher(topic);

                String collabroomId = null;
                while(m.find()) {
                    collabroomId = m.group(1);
                    break;
                }

                if(collabroomId != null) {
                    boolean isaLayer = false;

                    String layername = "R" + collabroomId;
                    if(geoserverList != null) {
                        isaLayer = geoserverList.contains(layername);
                    } else {
                        geoserverList = new ArrayList<String>();
                    }

                    //Create a new Room layer by calling run if its not in either the geoserver list or kml file
                    // directory
                    if(!isaLayer) {
                        log.info("Running full sync, found new feature with room id: " + layername);
                        run();
                    }
                }

                //TODO: add in check for collab room in geoserverList. if it's not there, get the collabroom
                // (probably from the db) and run syncRooms on that room

            } else if(topic.endsWith("newcollabroom")) { //new room message
                String layername = "R" + String.valueOf(msg.getInt("collabRoomId"));
                boolean isaLayer = geoserverList.contains(layername);

                //Create a new Room layer by calling run if its not in either the geoserver list or kml file directory
                if(!isaLayer) {
                    log.info("Running full sync, found new room id: " + layername);
                    run();
                }

                //TODO: add in check for collab room in geoserverList (this may not be necessary here
                //since its new?). if it's not there, get the collabroom (probably from the db) and run syncRooms on 
                //that room

            } /*else if (topic.endsWith("createIncident")) { //new incident message
                JSONObject msgData = msg.getJSONObject("messageData");
                String layername = "I" + String.valueOf(msgData.getString("incidentId"));
                boolean isaLayer = geoserverList.contains(layername);
        		boolean isaKml = kmlList.contains(layername);
        		
        		
                //Create a new Incident layer by calling run if its not in the geoserver list
                if (!isaLayer) {
                	log.info("Running full sync, found new incident name: " + layername);
                	run();
                }
                
              //TODO: add in check for incident in geoserverList (this may not be necessary here since its new?). if 
               * it's not there, get 
               * the incident (probably from the db) and run syncIncidents on that incident
            } */
            	
        	/*else if (msg.getString("topic").endsWith("createMyMapsRoom")) {
            	// create new view for a users MyMaps
            	JSONObject msgData = msg.getJSONObject("messageData");
            	int userId = msgData.getInt("userId");
            	if (userId > 0)
            	{
            		log.info("Adding MyMaps view for userid: " + userId);
                	geoserver.addMyMapsView(userId);	
            	} else
            	{
            		log.info("Got invalid user id - not creating mymaps view");
            	}
            }*/

        } catch(JSONException ex) {
            ex.printStackTrace();
            log.error("Exception: " + ex);
        }

    }


    /**
     * The timer event. Synchronize the DB, geoserver, and kml docs
     */
    @Override
    public void run() {
        log.info("Starting Timertask");
        EntityManager em = null;
        try {

            log.debug("emf" + emf);
            // Connect to the DB
            em = emf.createEntityManager();

            // query for collabrooms
            Query q = em.createQuery("FROM CollabRoom");
            List<CollabRoom> collabRooms = q.getResultList();

            // query for all incidents
            q = em.createQuery("FROM Incident");
            List<Incident> incidents = q.getResultList();

            //close DB connection
            em.close();

            //update list of db layers
            //dbList = (List<String>) CollectionUtils.collect(collabRooms, new BeanToPropertyValueTransformer
            // ("collabroomid"));
            //dbList.addAll((List<String>) CollectionUtils.collect(incidents, new BeanToPropertyValueTransformer
            // ("incidentid")));

            //dbList = new ArrayList<String>();
            //for (CollabRoom room : collabRooms)
            //	dbList.add(String.valueOf(room.getCollabRoomId()));
            //for (Incident incident : incidents)
            //	dbList.add(incident.getIncidentname());

            //log.debug(dbList);

            //update list of geoserver layers
            geoserverList = geoserver.getFeatureTypeList();

            if(geoserverList == null) {
                geoserverList = new ArrayList<String>();
            }

            log.debug(geoserverList);


            //sync collabrooms and incidents
            syncRooms(collabRooms);
            syncIncidents(incidents);

        } catch(Exception ex) {
            ex.printStackTrace();
            log.error("Error accessing DB " + ex);
        } finally {
            //make sure DB connection is closed
            if(em.isOpen()) {
                em.close();
            }
        }

    }

    private void syncRooms(List<CollabRoom> collabRooms) {
        EntityManager em = null;
        try {
            em = emf.createEntityManager();
            for(CollabRoom room : collabRooms) {
                String layername = "R" + String.valueOf(room.getCollabRoomId());
                String query1 =
                        "SELECT f.* FROM Feature f, CollabroomFeature cf WHERE cf.featureid=f.featureid AND cf" +
                                ".collabroomid = " +
                                room.getCollabRoomId() + " and deleted='f'";
                boolean hasFeatures = !em.createNativeQuery(query1, HibernateFeature.class).getResultList().isEmpty();
                boolean roomActive = room.getIncident() != null && room.getIncident().getActive();
                boolean noIncident = room.getIncidentid() == 0; //0 means no incident

                if(noIncident || (hasFeatures && roomActive)) {
                    boolean isaLayer = false;
                    if(geoserverList != null) {
                        isaLayer = geoserverList.contains(layername);
                    } else {
                        geoserverList = new ArrayList<String>();
                    }

                    if(!isaLayer) {
                        log.info("Adding layer: " + room.getName() + " to geoserver");
                        geoserver.addCollabRoomView(room);
                        geoserverList.add(layername);
                    }
                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
            log.error("Error accessing DB " + ex);
        } finally {
            //make sure DB connection is closed
            em.close();
        }
    }

    private void syncIncidents(List<Incident> incidents) {
        for(Incident incident : incidents) {

            if(incident.getActive()) {
                String layername = "I" + String.valueOf(incident.getIncidentid());
                boolean isaLayer = geoserverList.contains(layername);

                //Update geoserver
                if(!isaLayer) {
                    log.info("Adding layer: " + incident.getIncidentname() + " to geoserver");
                    geoserver.addIncidentView(incident);
                    geoserverList.add(layername);
                }
            }
        }
    }

    //getters and setters

    public String getLog4jPropertyFile() {
        return log4jPropertyFile;
    }

    public void setLog4jPropertyFile(String log4jPropertyFile) {
        this.log4jPropertyFile = log4jPropertyFile;
    }

    public String getDbuser() {
        return dbuser;
    }

    public void setDbuser(String dbuser) {
        this.dbuser = dbuser;
    }

    public String getDbpass() {
        return dbpass;
    }

    public void setDbpass(String dbpass) {
        this.dbpass = dbpass;
    }

    public String getDbname() {
        return dbname;
    }

    public void setDbname(String dbname) {
        this.dbname = dbname;
    }

    public String getDbhost() {
        return dbhost;
    }

    public void setDbhost(String dbhost) {
        this.dbhost = dbhost;
    }

    public String getDbport() {
        return dbport;
    }

    public void setDbport(String dbport) {
        this.dbport = dbport;
    }

    public String getGeoserverUrl() {
        return geoserverUrl;
    }

    public void setGeoserverUrl(String geoserverUrl) {
        this.geoserverUrl = geoserverUrl;
    }

    public String getGeoserverUsername() {
        return geoserverUsername;
    }

    public void setGeoserverUsername(String geoserverUsername) {
        this.geoserverUsername = geoserverUsername;
    }

    public String getGeoserverPassword() {
        return geoserverPassword;
    }

    public void setGeoserverPassword(String geoserverPassword) {
        this.geoserverPassword = geoserverPassword;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    public String getDataStoreName() {
        return dataStoreName;
    }

    public void setDataStoreName(String dataStoreName) {
        this.dataStoreName = dataStoreName;
    }
}
