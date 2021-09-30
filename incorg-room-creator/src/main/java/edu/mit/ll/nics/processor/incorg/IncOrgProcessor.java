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
package edu.mit.ll.nics.processor.incorg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import edu.mit.ll.nics.common.entity.IncidentOrg;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.log4j.PropertyConfigurator;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Processes Incident notifications for handling the automated creation of collaboration rooms based on being
 * registered for an incidenttype
 */
public class IncOrgProcessor implements Processor {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(IncOrgProcessor.class);

    /**
     * GsonBuilder instance
     */
    private static GsonBuilder gsonBuilder;

    /**
     * Main Gson instance
     */
    private static Gson gson;

    /**
     * Holds the JSONArray room config as parsed from the contents of the {@link IncOrgProcessor#roomsConfig}
     */
    private JSONArray roomsJsonArray = null;

    /**
     * The template read out of the room config property. The first string variable that appears is the Room Name
     * specified in the rooms config, and the second string variable is the organization name.
     */
    private String roomNameTemplate;

    /**
     * Default value for {@link IncOrgProcessor#roomNameTemplate)}
     */
    private String roomNameTemplateDefault = "%s (%s)";

    /**
     * The userorgid of the configured identity user, based on the identityOrgId the user is a super in
     */
    private int identityUserOrgId = -1;

    /**
     * The usersessionid for the userorg of the identity user that they should be a super in
     */
    private long identityUsersessionId = -1;

    // Properties

    /**
     * The log4j.properties file used by the Spring application
     */
    private String log4jPropertyFile;

    /**
     * The HTTP Header string used for specifying the identity username
     */
    private String identityHeader;

    /**
     * The NICS username of the user the processor will use to create rooms
     */
    private String identityUser;

    /**
     * The organizationid of the org the identityUser is a super user in
     */
    private int identityOrgId;

    /**
     * The configured endpoint of the em-api instance. Expected to be local access to port 8080.
     * <p>e.g., http://localhost:8080/em-api/v1</p>
     */
    private String emapi;

    /**
     * A json string configuring each room to create, as well as whether or not it should be secured.
     * <p>
     * {"rooms": [ {"roomName":"Working Map", "isSecured":false}, {"roomName":"Command", "isSecured": true} ] }
     * </p>
     */
    private String roomsConfig;

    /**
     * Routing key of incident added notifications, for filtering incoming messages
     */
    private String incidentAddedTopic;

    /**
     * Routing key of incident added notifications for superuser, for filtering incoming messages
     */
    private String incidentAddedTopicSuper;

    /**
     * Routing key of incident updated notifications, for filtering incoming messages
     */
    private String incidentUpdatedTopic;

    /**
     * Routing key of incident org added notifications, for filtering incoming messages
     */
    private String incidentOrgAddedTopic;

    /**
     * Routing key pattern of incident added notifications, for filtering incoming messages
     */
    private String incidentAddedPattern;

    /**
     * Routing key pattern of incident added notifications for superusers, for filtering incoming messages
     */
    private String incidentAddedPatternSuper;

    /**
     * Routing key pattern of incident updated notifications, for filtering incoming messages
     */
    private String incidentUpdatedPattern;

    /**
     * Routing key pattern of incident org added notifications, for filtering incoming messages
     */
    private String incidentOrgAddedPattern;

    /**
     * Whether or not to take into account if the Org is registered to the incident type to get
     * a room created, or if ALL orgs added (after initial creation) get rooms created.
     */
    private boolean createRoomsRegardlessOfRegistration;

    // TODO: should just use gson to get Org entities?
    private static ConcurrentHashMap<Integer, JSONObject> allOrgIdToOrgMap;



    /**
     * Default constructor, required by Spring
     */
    public IncOrgProcessor() {
    }

    /**
     * This method is called by Spring once all the properties have been read as specified in the spring .xml file
     */
    public void init() {
        PropertyConfigurator.configure(log4jPropertyFile);

        gsonBuilder = new GsonBuilder();
        class MyDateTypeAdapter extends TypeAdapter<Date> {
            @Override
            public void write(JsonWriter out, Date value) throws IOException {
                if(value == null) {
                    out.nullValue();
                } else {
                    out.value(value.getTime() / 1000);
                }
            }

            @Override
            public Date read(JsonReader in) throws IOException {
                if(in != null) {
                    return new Date(in.nextLong() * 1000);
                } else {
                    return null;
                }
            }
        }

        gsonBuilder.registerTypeAdapter(Date.class, new MyDateTypeAdapter());
        gson = gsonBuilder.create();

        populateIdentityUserorgId();

        parseRoomConfig();

        populateOrgs();

    }

    private void populateIdentityUserorgId() {
        JSONObject identityUserOrg = getUserOrg(1, identityOrgId, identityUser);
        identityUserOrgId = identityUserOrg.optInt("userorgid", -1);
        if(identityUserOrgId == -1) {
            LOG.error("Failed to get userorgid for identityuser! Cannot continue.");
            System.exit(1); // TODO: Use error code keys, that would specifically point to this reason
        }
    }

    /**
     * Fetches the identity user entity with the given workspaceId, and the previously set
     * userorgid. The user entity returned gets the usersessionid added.
     *
     * @param workspaceId the id of the workspace the identity user is a super in
     *
     */
    private void populateIdentityUsersessionId(int workspaceId) {

        if(identityUsersessionId == -1) {
            JSONObject user = getUserWithUsersessionId(workspaceId, identityUserOrgId, true);
        }
    }

    /**
     * Fetches a User entity via the specified userorgId, and includes a usersessionid, which is necessary
     * for some api calls. You can also specify whether this user's usersesssionId is to be set as the
     * identity user's usersessionid for this session. NOTE: if there was no usersessionid attached, a
     * default of -1 is set, so the caller needs to verify the usersessionid is >0 to be valid.
     *
     * @param workspaceId the ID of the workspace the UserOrg belongs to
     * @param userorgId the ID of the UserOrg to get the session for
     * @param setAsIdentityUsersessionId if True, sets the global identityUsersessionId to the one found
     *                                   by the given userorgId
     *
     * @return a JSONObject representing a User object with an associated UsersessionId property added if successful,
     *         null otherwise
     */
    private JSONObject getUserWithUsersessionId(int workspaceId, int userorgId, boolean setAsIdentityUsersessionId) {

        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        String url = String.format("%s/users/%d/userWithSession/userorg/%d", emapi, workspaceId, userorgId);
        Response response = client.target(url)
                .request()
                .header(identityHeader, identityUser)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();

        int status = response.getStatus();
        String userResponse = response.readEntity(String.class);
        response.close();
        LOG.debug("\tGet User with Session: {}\n\t{}", status, userResponse);

        if(status != 200) {
            return null;
        }

        JSONObject jsonResponse = new JSONObject(userResponse);
        JSONArray jsonUsers = jsonResponse.optJSONArray("users");
        JSONObject user = jsonUsers.getJSONObject(0);
        JSONArray curUserSessions = user.optJSONArray("currentusersessions");
        JSONObject curUserSession = curUserSessions.optJSONObject(0);
        long usersessionId = curUserSession.optLong("usersessionid", -1);

        // Add usersessionid property
        user.put("usersessionid", usersessionId);

        if(setAsIdentityUsersessionId) {
            identityUsersessionId = usersessionId;
        }

        return user;
    }


    /**
     * Parses the {@link IncOrgProcessor#roomsConfig} property, and sets the parsed array of rooms into the {@link
     * IncOrgProcessor#roomsJsonArray} member.
     */
    private void parseRoomConfig() {

        JSONObject roomsConfigJson = null;
        try {
            roomsConfigJson = new JSONObject(roomsConfig);
            roomsJsonArray = roomsConfigJson.getJSONArray("rooms");
            LOG.debug("Got rooms config: {}", roomsJsonArray.toString());

            roomNameTemplate = roomsConfigJson.optString("template", roomNameTemplateDefault);

        } catch(JSONException e) {
            LOG.error("Error parsing roomsConfig property!", e);
            System.exit(1); // TODO: give error code mapped to this reason
        }

    }

    /**
     * Re-initializes and populates the {@link IncOrgProcessor#allOrgIdToOrgMap} by calling
     * {@link IncOrgProcessor#getAllOrgs(int)}. Will exit the application of no Organizations are returned.
     */
    public void populateOrgs() {
        allOrgIdToOrgMap = new ConcurrentHashMap<>();
        JSONArray orgs = getAllOrgs(1);
        if(orgs == null || orgs.isEmpty()) {
            LOG.error("Failed populating Organizations from API, cannot continue");
            System.exit(1);
        }

        JSONObject curOrg = null;
        for(int i = 0; i < orgs.length(); i++) {
            curOrg = orgs.getJSONObject(i);
            addOrgToMap(curOrg.optInt("orgId"), curOrg);
        }

        LOG.debug("Populated all orgs map: {}", allOrgIdToOrgMap.size());
    }

    /**
     * Helper method for adding an orgId->org mapping to {@link IncOrgProcessor#allOrgIdToOrgMap}. Only adds
     * the mapping of orgId > 0 and the org parameter is not null.
     *
     * @param orgId the ID of the organization being added
     * @param org the JSONObject representing the Organization being added
     */
    private void addOrgToMap(int orgId, JSONObject org) {
        if(orgId > 0 && org != null) {
            allOrgIdToOrgMap.put(orgId, org);
        }
    }

    /**
     * Implementation of process method on the exchange. Receives JSON messages from rabbitmq sent by the API.
     *
     * @param exchange exchange passed into route, contains the incoming message
     */
    @Override
    public void process(Exchange exchange) {
        // Read the incoming message
        String body = exchange.getIn().getBody(String.class);
        LOG.debug("Processing Message: " + body);

        String routingKey = exchange.getIn().getHeader("rabbitmq.ROUTING_KEY").toString();
        LOG.debug("GOT routingKey:\n{}", routingKey);
        LOG.info("Message: {}", body);

        // TODO: Could split this up into multiple processors with some common library/utility classes
        if(routingKey.matches(incidentAddedPattern) || routingKey.equals(incidentAddedTopic) ||
            routingKey.matches(incidentAddedPatternSuper) || routingKey.equals(incidentAddedTopicSuper)) {
            LOG.debug("Received incident added: {}", routingKey);
            handleIncidentAdded(body);
        } else if(routingKey.matches(incidentUpdatedPattern) || routingKey.equals(incidentUpdatedTopic)) {
            LOG.debug("Received Incident Updated: {}", routingKey);
            handleIncOrgsAdded(body);
        } else if(routingKey.matches(incidentOrgAddedPattern) || routingKey.equals(incidentOrgAddedTopic)) {
            LOG.debug("Received IncidentOrg added: {}", routingKey);
            handleIncOrgsAdded(body);
        } else if(routingKey.contains("incidentEscalation")) {
            LOG.debug("Received Incident Escalation: {}", routingKey);
            handleEscalateIncident(body);
        } else {
            LOG.warn("UNSUPPORTED topic: {}", routingKey);
        }
    }

    /**
     * Fetches the specified Org, and returns the Org as a JSONObject
     *
     * @param workspaceId the ID of the workspace the Org is in
     * @param orgId the ID of the Organization to fetch
     *              
     * @return a JSONObject of the Org requested if successful/found, null otherwise
     */
    private JSONObject getOrg(int workspaceId, int orgId) {
        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        String url = String.format("%s/orgs/%d/org/id/%d", emapi, workspaceId, orgId);
        Response response = client.target(url)
                .request()
                .header(identityHeader, identityUser)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();

        int status = response.getStatus();
        String orgsResponse = response.readEntity(String.class);
        response.close();
        LOG.debug("\tGet Org by Id: {}\n\t{}", status, orgsResponse);

        if(status != 200) {
            return null;
        }

        JSONObject jsonResponse = new JSONObject(orgsResponse);
        JSONArray jsonOrgs = jsonResponse.optJSONArray("organizations");

        // Should only be one, or none
        if(jsonOrgs != null && jsonOrgs.length() == 1) {
            return jsonOrgs.getJSONObject(0);
        } else {
            return null;
        }
    }

    /**
     * Fetch all Organizations. The API currently does NOT even use the workspaceId in the
     * query, so ALL Orgs are returned.
     *
     * @param workspaceId the ID of the workspace to get the Organizations from
     *
     * @return JSONArray of Organization objects if successful, null otherwise
     */
    private JSONArray getAllOrgs(int workspaceId) {
        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        String url = String.format("%s/orgs/%d/all", emapi, workspaceId);
        Response response = client.target(url)
                .request()
                .header(identityHeader, identityUser)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();

        int status = response.getStatus();
        String orgsResponse = response.readEntity(String.class);
        response.close();
        LOG.debug("\tAll Organizations: {}\n\t{}", status, orgsResponse);

        if(status != 200) {
            return null;
        }

        JSONObject jsonResponse = new JSONObject(orgsResponse);
        JSONArray jsonOrgs = jsonResponse.optJSONArray("organizations");

        return jsonOrgs;
    }

    /**
     * Retrieves list of Organizations that are registered to the IncidentType on the specified Incident.
     *
     * @param workspaceId the workspaceId the incident belongs to
     * @param incidentId  the id of the incident
     *
     * @return a JSONArray containing organization entities if successful, null otherwise
     */
    private JSONArray getOrgsRegisteredForIncident(int workspaceId, int incidentId) {

        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        String url = String.format("%s/orgs/%d/incidenttype/%d/org", emapi, workspaceId, incidentId);
        Response response = client.target(url)
                .request()
                .header(identityHeader, identityUser)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();

        int status = response.getStatus();
        String orgsResponse = response.readEntity(String.class);
        response.close();
        LOG.debug("\tOrgResponse: {}\n\t{}", status, orgsResponse);

        if(status != 200) {
            return null;
        }

        JSONObject jsonResponse = new JSONObject(orgsResponse);
        JSONArray jsonOrgs = jsonResponse.optJSONArray("organizations");

        return jsonOrgs;
    }

    /**
     * Retrieve array of incidentorg mappings for the specified incidentid.
     * <p>Example: {"orgid":1,"incidentid":395,"userid":4,"created":1591039971188}</p>
     *
     * @param workspaceId the ID of the workspace the incident is in
     * @param incidentId  the ID of the incident to get the mappings from
     *
     * @return a JSONArray containing the incidentOrg object(s) if successful, null otherwise
     */
    private JSONArray getIncidentOrgs(int workspaceId, int incidentId) {

        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        String getIncOrgsEndpoint = String.format("%s/incidents/%d/orgs/%d", emapi, workspaceId, incidentId);
        Response getIncOrgsResponse = client.target(getIncOrgsEndpoint)
                .request()
                .header(identityHeader, identityUser)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();

        int status = getIncOrgsResponse.getStatus();
        String strGetIncOrgsResponse = getIncOrgsResponse.readEntity(String.class);
        LOG.debug("Got getIncidentOrgs response: {}\n{}", status, strGetIncOrgsResponse);
        JSONObject response = new JSONObject(strGetIncOrgsResponse);

        return response.optJSONArray("incidentOrgs");
    }

    /**
     * Posts IncidentOrg mappings to the API.
     *
     * @param workspaceId   id of the workspace the incident belongs to
     * @param incidentId    id of the incident to post mapping for
     * @param usersessionId sessionid from the incident to use as the user making the post
     * @param orgIds        a list of organization ids to add IncidentOrg mappings for
     *
     * @return A JSONObject containing the response from the API if successful, null otherwise
     */
    private JSONObject addIncidentOrgs(int workspaceId, int incidentId, long usersessionId, List<Integer> orgIds) {

        JSONObject user = getUser(workspaceId, usersessionId);
        if(user == null) {
            LOG.error("Failed to get user from usersessionId {}, can't continue", usersessionId);
            return null;
        }

        int userId = user.getInt("userId");
        List<IncidentOrg> incOrgsToAdd = new ArrayList<>();
        for(int orgId : orgIds) {
            incOrgsToAdd.add(new IncidentOrg(orgId, incidentId, userId));
        }

        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        String addIncOrgsEndpoint = String.format("%s/incidents/%d/orgs/%d", emapi, workspaceId, incidentId);
        Response addIncOrgsResponse = client.target(addIncOrgsEndpoint)
                .request()
                .header(identityHeader, identityUser)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(gson.toJson(incOrgsToAdd), MediaType.APPLICATION_JSON_TYPE));

        String strAddIncOrgsResponse = addIncOrgsResponse.readEntity(String.class);
        addIncOrgsResponse.close();

        int status = addIncOrgsResponse.getStatus();
        LOG.debug("Got response from adding orgs to incident: {}\n{}", status, strAddIncOrgsResponse);

        if(status != 200) {
            LOG.debug("Bad response from posting incidentorgs: {} - {}",
                    strAddIncOrgsResponse,
                    addIncOrgsResponse.getStatusInfo().getReasonPhrase());
            return null;
        }

        // IncidentServiceResponse with just a count and success/status
        JSONObject jsonIncOrgsResponse = new JSONObject(strAddIncOrgsResponse);
        int numOrgsAdded = jsonIncOrgsResponse.optInt("count");
        // Comparing to size of input is misleading, they could have already been added
        LOG.debug("Number of orgs added to incident: {}/{}", numOrgsAdded, orgIds.size());

        return jsonIncOrgsResponse;
    }

    /**
     * Handler for "Incident Added" notifications. Uses the Incident in the notification to perform the checks
     * necessary to automatically add orgs to the incident that are registered for the incidenttype(s).
     *
     * @param message JSON notification sent to the Incident Added topic
     */
    private void handleIncidentAdded(String message) {
        JSONObject incident = new JSONObject(message);
        LOG.debug("\n\nINCIDENT ADDED\n\n");

        int workspaceId = incident.getInt("workspaceid");
        int incidentId = incident.getInt("incidentid");
        long usersessionId = incident.getLong("usersessionid");

        JSONArray jsonOrgs = getOrgsRegisteredForIncident(workspaceId, incidentId);
        if(jsonOrgs != null && jsonOrgs.length() > 0) {
            List<Integer> orgIds = new ArrayList<>();
            for(int i = 0; i < jsonOrgs.length(); i++) {
                orgIds.add(jsonOrgs.getJSONObject(i).getInt("orgId"));
            }
            LOG.debug("\n\nADDING INCIDENT ORGS\n\n");
            JSONObject addIncOrgsResponse = addIncidentOrgs(workspaceId, incidentId, usersessionId, orgIds);
            // TODO: possibly put out notification about success/failure... alert or email or something
        } else {
            LOG.debug("NO REGISTERED ORGS to add...");
        }

    }

    /**
     * Parses out the Incident from the escalate incident message, and passes on to
     * {@link IncOrgProcessor#escalateIncident(int, int, long)} to perform the escalation
     *
     * @param message the escalation message sent by the UI, containing an incident
     */
    private void handleEscalateIncident(String message) {
        LOG.debug("Escalation incident message....");

        JSONObject incident = new JSONObject(message);
        final int workspaceId = incident.getInt("workspaceid");
        final int incidentId = incident.getInt("incidentid");
        final long usersessionId = incident.getLong("usersessionid");

        LOG.debug("Parsed workspaceId({}}), incidentId({}}), and usersessionId({}}) out of escalation message:",
                workspaceId, incidentId, usersessionId);

        escalateIncident(workspaceId, incidentId, usersessionId);
    }

    /**
     * Escalates the specified Incident. All Orgs associated with this Incident are checked to see if they
     * have parent organizations. Parent/Child mappings are built, and if any Parents exist for the Orgs
     * associated with this Incident, a batch call to
     * {@link IncOrgProcessor#doCreateRooms(int, long, JSONArray, int, JSONArray)} is made, creationg
     * Parent/Child collaboration rooms for each Parent/Child mapping.
     *
     * @param workspaceId the ID of the workspace the Incident belongs to
     * @param incidentId the ID of the Incident
     * @param usersessionId the ID of the usersession being used to escalate the Incident
     *                      
     */
    private void escalateIncident(int workspaceId, int incidentId, long usersessionId) {
        LOG.debug("Escalating incident {}", incidentId);

        JSONArray incOrgs = getIncidentOrgs(workspaceId, incidentId);
        if(incOrgs == null || incOrgs.length() == 0) {
            LOG.info("No incidentorgs on incident to escalate from");
            return;
        }
        LOG.debug("Got incident orgs for incidentid {}:\n'{}'", incidentId, incOrgs);

        Map<Integer, Integer> childToParentIdMap = new HashMap<>();

        JSONObject curIncOrg;
        JSONObject curOrg;
        for(int i = 0; i < incOrgs.length(); i++) {
            curIncOrg = incOrgs.optJSONObject(i);
            LOG.debug("Checking current IncOrg for parent...\n{}", curIncOrg.toString());
            if(curIncOrg != null) {
                curOrg = allOrgIdToOrgMap.get(curIncOrg.getInt("orgid"));
                int curOrgParentId = curOrg.optInt("parentorgid", -1);
                LOG.debug("Got parentorgid: {}", curOrgParentId);
                if(curOrgParentId > 0) {
                    childToParentIdMap.put(curIncOrg.getInt("orgid"), curOrgParentId);
                }
            }
        }

        if(childToParentIdMap.isEmpty()) {
            LOG.debug("CHILD TO PARENT MAP IS EMPTY!!");
            return;
        }

        JSONArray parentOrgs = new JSONArray();
        JSONArray childOrgs = new JSONArray();
        Map<JSONObject, Integer> childOrgToParentIdMap = new HashMap<>();
        
        Set<Map.Entry<Integer, Integer>> keyVal = childToParentIdMap.entrySet();
        for(Map.Entry<Integer, Integer> entry : keyVal) {
            JSONObject childOrg = allOrgIdToOrgMap.get(entry.getKey());
            JSONObject parentOrg = allOrgIdToOrgMap.get(entry.getValue());

            parentOrgs.put(parentOrg);
            childOrgs.put(childOrg);
            // TODO: just pass childToParentIdMap? would have to do if contains(parentid) instead of get(parentid) to
            //  get child...... but there can be multiple parents, so they're not unique for a map, so... pass
            //  map of childorg->parent org? And maybe can pull that way?
            childOrgToParentIdMap.put(childOrg, entry.getValue());
        }

        doCreateRooms(workspaceId, usersessionId, parentOrgs, incidentId, childOrgs);
    }

    /**
     * Hits the API to get the list of Organizations that are parents of a set of IncidentOrgs on an Incident
     *
     * @param workspaceId the id of the workspace the incident belongs to
     * @param incidentId the id of the incident to get incidentorgs from
     *
     * @return An JSONArray of Organization objects
     */
    private JSONArray getOrganizationParents(int workspaceId, int incidentId) {
        // TODO: not being used... investigate and remove/or use
        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        String url = String.format("%s/orgs/%d/parents/incident/%d", emapi, workspaceId, incidentId);
        Response response = client.target(url)
                .request()
                .header(identityHeader, identityUser)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();

        int status = response.getStatus();
        String orgsResponse = response.readEntity(String.class);
        response.close();
        LOG.debug("\tGet Org Parents Response: {}\n\t{}", status, orgsResponse);

        if(status != 200) {
            return null;
        }

        JSONObject jsonResponse = new JSONObject(orgsResponse);
        JSONArray jsonOrgs = jsonResponse.optJSONArray("organizations");

        return jsonOrgs;
    }

    /**
     * Handler for "Incident Org Added" notifications. Uses the Incident in the notification to perform the checks
     * necessary to automatically add orgs to the incident that are registered for the incidenttype(s), and create the
     * configured collaboration rooms for these incidents.
     *
     * @param message
     */
    private void handleIncOrgsAdded(String message) {
        Collection<IncidentOrg> incidentOrgs = null;
        try {
            // Get the workspaceId and incidentId from the notification
            JSONObject incident = new JSONObject(message);
            int workspaceId = incident.optInt("workspaceid", -1);
            int incidentId = incident.optInt("incidentid", -1);
            long usersessionId = incident.optLong("usersessionid", -1);

            if(workspaceId == -1 || incidentId == -1 || usersessionId == -1) {
                LOG.debug("Invalid ID(s) in notification, can't process:\n{}", message);
                return;
            }

            // TODO: when creating an incident that's by an org set to restrict incidents, this
            //  is the first notification seen... so can't jump to creating rooms. May need to
            //  check and see that all registered orgs are also added by passing along to that
            //  handler?

            createRooms(workspaceId, incidentId, usersessionId);

        } catch(Exception e) {
            LOG.error("Exception processing notification", e);
        }
    }

    /**
     * Gets all organizations registered for the incidenttypes set on the specified incident, then for each organization
     * makes a call to {@link IncOrgProcessor#doCreateRooms(int, long, JSONArray, int)} to create the room.
     *
     * @param workspaceId   id of the workspace the incident belongs to
     * @param incidentId    id of the incident
     * @param usersessionId usersessionId of the identity user
     */
    private void createRooms(int workspaceId, int incidentId, long usersessionId) {

        JSONArray orgs;

        // First get registered orgs
        orgs = getOrgsRegisteredForIncident(workspaceId, incidentId);

        // If creating rooms regardless, now see about adding any part of incidentorgs
        if(createRoomsRegardlessOfRegistration) {
            // Get incidentorg mappings
            JSONArray incidentOrgs = getIncidentOrgs(workspaceId, incidentId);

            if(orgs == null) {
                orgs = new JSONArray();
            }

            // Get org from orgid for each mapping
            for(int i = 0; i < incidentOrgs.length(); i++) {
                JSONObject incOrg = incidentOrgs.optJSONObject(i);
                JSONObject curOrg = allOrgIdToOrgMap.get(incOrg.getInt("orgid"));

                // If org isn't in map, try fetching it
                if(curOrg == null) {
                    curOrg = getOrg(workspaceId, incOrg.getInt("orgid"));
                    if(curOrg != null) {
                        addOrgToMap(curOrg.getInt("orgId"), curOrg);
                    }
                }

                if(curOrg != null) {
                    // TODO: should really check to see if the org is already in there from
                    //  the previous step in getOrgsRegisteredForIncident...
                    orgs.put(curOrg);
                }
            }

        }

        doCreateRooms(workspaceId, usersessionId, orgs, incidentId);
    }

    /**
     * Convenience method for calling {@link IncOrgProcessor#doCreateRooms(int, long, JSONArray, int, JSONArray)}
     * with a null childOrgs parameter.
     *
     * @param workspaceId 
     * @param usersessionId 
     * @param orgs
     * @param incidentId
     * 
     * @see #IncOrgProcessor()#doCreateRooms(int, long, JSONArray, int, JSONArray) 
     */
    private void doCreateRooms(int workspaceId, long usersessionId, JSONArray orgs, int incidentId) {
        doCreateRooms(workspaceId, usersessionId, orgs, incidentId, null);
    }

    /**
     * Utility method that handles reading the rooms config, and for each org being added, creates each room in the
     * rooms config for that org by calling {@link IncOrgProcessor#createCollabRoom(int, long, JSONObject, int, String,
     * boolean, JSONObject)}
     *
     * @param workspaceId   the id of the workspace the incident belongs to
     * @param usersessionId the usersessionid of the identity user
     * @param orgs           the organization entity for which the rooms are being created
     * @param incidentId    the id of the incident the rooms are being added to
     * @param childOrgs      the child org entity, if creating a parent/child room
     */
    private void doCreateRooms(int workspaceId, long usersessionId, JSONArray orgs, int incidentId,
                               JSONArray childOrgs) {

        Map<Integer, String> orgIdRoomNameMap = new HashMap<>();

        LOG.debug("\n\nEntered doCreateRooms, with {} orgs and child orgs: {}", orgs.length(), childOrgs);
        JSONArray roomEntities = new JSONArray();
        JSONObject childOrg = null;
        for(int o = 0; o < orgs.length(); o++) {
            JSONObject org = orgs.getJSONObject(o);
            JSONObject roomResponse = null;

            LOG.debug("Working with org {}", org.optInt("orgId"));

            for(int i = 0; i < roomsJsonArray.length(); i++) {
                JSONObject room = roomsJsonArray.getJSONObject(i);
                String curRoomNameKey = String.format("%s-%s", room.optString("roomName"),
                        org.optString("prefix"));
                LOG.debug("\troom {}", curRoomNameKey);
                if(childOrgs != null && childOrgs.length() > 0) {
                    LOG.debug("\t\tProcessing Child orgs");
                    for(int c = 0; c < childOrgs.length(); c++) {
                        childOrg = null;
                        childOrg = childOrgs.getJSONObject(c);
                        LOG.debug("\t\t\tChild org {}", childOrg.optInt("orgId"));
                        if(childOrg.optInt("parentorgid") != org.optInt("orgId")) {
                            LOG.debug("\t\t\tchild org's parent id does NOT match current orgid");
                            continue;
                        }
                        LOG.debug("\t\t\tchild org's parent id DOES match current orgid, so creating room {}",
                                room.getString("roomName"));

                        if(orgIdRoomNameMap.containsValue(String.format("%s-%s", room.optString("roomName"),
                                org.optString("prefix")))) {
                            // SKIP
                            LOG.debug("Skipping... room already exists...{}",
                                    String.format("%s-%s", room.optString("roomName"),
                                            org.optString("prefix")));
                            continue;
                        }

                        roomResponse = createCollabRoom(workspaceId, usersessionId, org, incidentId,
                                room.getString("roomName"),
                                room.getBoolean("isSecure"),
                                childOrg);

                        try {
                            if(roomResponse != null) {
                                LOG.debug("\t\t\tgot room back, so adding to batch");
                                roomEntities.put(roomResponse);
                                orgIdRoomNameMap.put(org.optInt("orgId"),
                                        String.format("%s-%s", room.optString("roomName"),
                                                org.optString("prefix")));
                            } else {
                                LOG.debug("\t\t\tDid NOT get room back, no room to add");
                            }
                        } catch(Exception e) {
                            LOG.error("Exception creating a room entity");
                        }
                    }

                } else {

                    LOG.debug("\tNO child orgs to process, so just create room:\n{} ({})",
                            room.opt("roomName"), org.opt("prefix"));

                    LOG.debug("Does map have {}? {}", curRoomNameKey, orgIdRoomNameMap.containsValue(curRoomNameKey));
                    LOG.debug("Map contents: " + orgIdRoomNameMap.toString());
                    if(orgIdRoomNameMap.containsValue(curRoomNameKey)) {
                        // SKIP
                        LOG.debug("Skipping... room already exists...{}",
                                curRoomNameKey);
                        continue;
                    }

                    roomResponse = createCollabRoom(workspaceId, usersessionId, org, incidentId,
                            room.getString("roomName"),
                            room.getBoolean("isSecure"),
                            childOrg); // todo: if no child org, make call to function w/o it?
                    LOG.debug("Created room: {}", room == null ? "null" : roomResponse.toString());

                    try {
                        if(roomResponse != null) {
                            LOG.debug("\t\t\tgot room back, so adding to batch");
                            roomEntities.put(roomResponse);
                            orgIdRoomNameMap.put(org.optInt("orgId"),
                                    curRoomNameKey);
                            LOG.debug("\t\t\tAdded room value: {}", curRoomNameKey);
                        } else {
                            LOG.debug("\t\t\tDid NOT get room back, no room to add");
                        }
                    } catch(Exception e) {
                        LOG.error("Exception creating a room entity");
                    }
                }
            }

        }

        LOG.debug("DONE processing rooms, so calling batch with {} rooms", roomEntities.length());

        callPostRoomsBatch(workspaceId, incidentId, roomEntities);
    }

    /**
     * Posts the given rooms to the em-api
     *
     * @param workspaceId the id of the workspace the rooms belong to
     * @param incidentId the id of the incident the rooms belong to
     * @param roomEntities the collaboration rooms to create
     */
    public void callPostRoomsBatch(int workspaceId, int incidentId, JSONArray roomEntities) {
        LOG.debug("Entered call post rooms batch, with {} rooms", roomEntities.length());

        String createCollabRoomEndpoint = String.format("/collabroom/%d/batch", incidentId);
        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();

        Response response = null;
        String strCreateCollabResponse = null;
        try {
            ResteasyWebTarget target = client.target(String.format("%s%s", emapi, createCollabRoomEndpoint))
                    .queryParam("userOrgId", identityUserOrgId)
                    //.queryParam("orgId", orgId)
                    .queryParam("workspaceId", workspaceId);

            response = target
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .header(identityHeader, identityUser)
                    .post(Entity.entity(roomEntities.toString(), MediaType.APPLICATION_JSON_TYPE));

            int status = response.getStatus();
            strCreateCollabResponse = response.readEntity(String.class);
            response.close();
            client.close();
            LOG.debug("Got collabroomResponse: {}\n{}", status, strCreateCollabResponse);
        } catch(Exception e) {
            LOG.error("Exception attempting to post collabroom", e);
        }

    }

    /**
     * Get current or past usersessionid for specified user.
     * 
     * @param workspaceId id of workspace user has/is logged into
     * @param orgId id of org user is/was signed in as
     * @param username the username of the user to get the usersessionid for
     *
     * @return
     */
    private long getUsersessionIdForUser(int workspaceId, int orgId, String username) {
        // TODO: implement... intention is to call some endpoint to get a valid usersessionid
        //  from the specified user, current or past
        // usersessionid set?
        return -1;
    }

    /**
     * Utility method for getting a user based on the usersessionId.
     *
     * @param workspaceId   the id of the workspace the usersessionId belongs to
     * @param usersessionId the usersessionid of the user to look for
     *
     * @return a JSONObject containing the user entity if successful, null otherwise
     */
    private JSONObject getUser(int workspaceId, long usersessionId) {
        LOG.debug("Entering getUser...");

        String getUserByCurrentSessionIdEndpoint = String.format("/users/%d/usersessionId/%d",
                workspaceId, usersessionId);
        String getUserByPastSessionIdEndpoint = String.format("/users/%d/pastUsersessionId/%d",
                workspaceId, usersessionId);

        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        Response response = client.target(String.format("%s%s", emapi, getUserByCurrentSessionIdEndpoint))
                .request().accept(MediaType.APPLICATION_JSON).header(identityHeader, identityUser)
                .get();

        int status = response.getStatus();
        String strResponse = response.readEntity(String.class);
        response.close();
        LOG.debug("Got user response: {}\n{}", status, strResponse);

        if(status != 200) {
            LOG.debug("No current usersession, looking for an old one next...");
            response = client.target(String.format("%s%s", emapi, getUserByPastSessionIdEndpoint))
                    .request().accept(MediaType.APPLICATION_JSON).header(identityHeader, identityUser)
                    .get();

            status = response.getStatus();
            strResponse = response.readEntity(String.class);
            response.close();
            LOG.debug("Got response from past usersession: {}\n{}", status, strResponse);

            if(status != 200) {
                LOG.debug("No old usersession either, returning null");
                return null;
            }
        }

        JSONObject userServiceResponse = new JSONObject(strResponse);
        JSONObject user = userServiceResponse.getJSONArray("users").getJSONObject(0);
        LOG.debug("DEBUG got User: \n{}", user.toString());

        return user;
    }

    /**
     * Gets the specified user's userorgs, and returns the one matching the specified organization if found.
     *
     * @param workspaceId the id of the workspace the userorg to retrieve is in
     * @param orgId       the id of the organization of the userorg to retrieve
     * @param username    the username of the uesr to look up
     *
     * @return a userorg entity if found, null otherwise
     */
    private JSONObject getUserOrg(int workspaceId, int orgId, String username) {

        String getUserOrgsEndpoint = String.format("/users/%d/userOrgs", workspaceId);
        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        Response response = client.target(String.format("%s%s", emapi, getUserOrgsEndpoint))
                .queryParam("userName", username)
                .request().accept(MediaType.APPLICATION_JSON).header(identityHeader, username)
                .get();

        int status = response.getStatus();
        String strUserOrgs = response.readEntity(String.class);
        response.close();
        LOG.debug("Got userorgs response: {}\n{}", status, strUserOrgs);

        JSONObject userResponse = new JSONObject(strUserOrgs);
        JSONArray userorgs = userResponse.getJSONArray("userOrgs");
        for(int i = 0; i < userorgs.length(); i++) {
            JSONObject userorg = userorgs.getJSONObject(i);
            if(userorg.getInt("orgid") == orgId) {
                // Decorate response with userId, too
                userorg.put("userid", userResponse.optInt("userId", -1));
                return userorg;
            }
        }

        return null;
    }

    /**
     * Utility method for getting a list of active users in an organization.
     *
     * @param workspaceId the id of the workspace the users are in
     * @param orgId       the id of the organization that the users belong
     *
     * @return a list of userIds if successful, null otherwise
     */
    private List<Integer> getUserIdsInOrg(int workspaceId, int orgId) {

        List<Integer> userIds = null;
        String enabledUsersEndpoint = String.format("%s/users/%d/enabled/%d", emapi, workspaceId, orgId);
        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();

        try {
            Response response = client.target(enabledUsersEndpoint)
                    .request()
                    .header(identityHeader, identityUser)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            int status = response.getStatus();
            String strEnabledUsersResponse = response.readEntity(String.class);
            response.close();
            LOG.debug("Got enabled users: {}\n{}", status, strEnabledUsersResponse);

            if(status == 200) {
                JSONObject jsonResponse = new JSONObject(strEnabledUsersResponse);
                JSONArray data = jsonResponse.optJSONArray("data");
                if(data != null && data.length() > 0) {
                    userIds = new ArrayList<>();
                    for(int i = 0; i < data.length(); i++) {
                        userIds.add(data.getJSONObject(i).getInt("userid"));
                    }
                } else {
                    LOG.debug("Getting users in org came back empty");
                }
            } else {
                LOG.debug("Got bad response, not returning users");
                return null;
            }

        } catch(Exception e) {
            LOG.error("Exception parsing enabled userids", e);
        }


        return userIds;
    }

    /**
     * Makes the actual request to the API to create the collaboration room.
     *
     * @param workspaceId   workspace of the incident
     * @param usersessionId sessionid of the user who created the incident
     * @param org           the Org entity the room will be created for
     * @param incidentId    the id of the incident to create the room on
     * @param roomName      the templated room name from the rooms config, to be combined with the Org.name
     * @param isSecure      whether or not this room is being secured to the specified org
     * @param childOrg      child org entity if this is an escalation
     *
     * @return A JSONObject of the CollabRoom for posting, null otherwise
     */
    private JSONObject createCollabRoom(int workspaceId, long usersessionId, JSONObject org, int incidentId,
                                    String roomName, boolean isSecure, JSONObject childOrg) {

        // TODO: identity user for now, since they have to be super to create secure rooms
        //  but could be worked around to use the creator of the incident, so can add logic
        //  here to use one that's specified, versus the identity user
        JSONObject user = getUserWithUsersessionId(workspaceId, identityUserOrgId, true);

        if(user == null) {
            LOG.debug("User not found, can't create collabroom");
            return null;
        }

        int orgId = org.optInt("orgId", -1);

        if(orgId == -1) {
            LOG.debug("Invalid orgId {}, not creating collabroom", orgId);
            return null;
        }

        if(identityUsersessionId <= 0) {
            LOG.debug("Identity usersessionId not set, can't continue creating room");
            return null;
        }

        String orgName = org.optString("name");
        if(orgName == null || orgName.equals("")) {
            LOG.error("Problem getting incoming org name, can't continue");
            return null;
        }

        boolean orgHasPrefix = false;
        String orgPrefix = org.optString("prefix");
        if(orgPrefix != null && !orgPrefix.equals("")) {
            orgHasPrefix = true;
        }

        String childOrgName = null;
        if(childOrg != null) {
            childOrgName = childOrg.optString("name");
            if(childOrgName == null || childOrgName.equals("")) {
                LOG.error("Problem getting incoming org name, can't continue");
                return null;
            }
        }

        boolean childHasPrefix = false;
        String childPrefix = null;
        if(childOrg != null) {
            childPrefix = childOrg.optString("prefix");
            if(childPrefix != null && !childPrefix.equals("")) {
                childHasPrefix = true;
            }
        }

        // TODO: extract to method, and offer possible separators/formatting options
        String fullRoomName;
        if(childOrg == null) {
            fullRoomName = String.format("%s (%s)", roomName, orgHasPrefix ? orgPrefix : orgName);
        } else {
            // Currently just using prefixes if it's a parent/child room? Can also do to regular room
            fullRoomName = String.format("%s (%s, %s)", roomName,
                    orgHasPrefix ? orgPrefix : orgName,
                    childHasPrefix ? childPrefix : childOrg);
        }
        LOG.debug("Creating room with name: {}", fullRoomName);

        JSONObject collabRoom = new JSONObject();
        collabRoom.put("incidentid", incidentId);
        collabRoom.put("usersessionid", identityUsersessionId);
        collabRoom.put("name", fullRoomName);

        if(isSecure) {
            LOG.debug("ROOM is marked secure, so looking up org users");

            // Can only happen if user creating incident is an admin, if they're not, then
            // a consumer super user needs to do it
            // Add users to adminUsers, readWriteUsers, readOnlyUsers lists accordingly
            // Although, what if a user is readOnly role? May need to further break it down
            // and add readOnly users to readOnly?

            // Add an admin, must do this for api to even secure room
            List<Integer> adminUsers = new ArrayList<>();
            adminUsers.add(user.getInt("userId"));
            collabRoom.put("adminUsers", adminUsers);

            // Add users in org
            List<Integer> userIdsInOrg = getUserIdsInOrg(workspaceId, orgId);
            if(userIdsInOrg != null && !userIdsInOrg.isEmpty()) {
                LOG.debug("Adding readWriteUsers to collabroom...");
                collabRoom.put("readWriteUsers", userIdsInOrg);
                LOG.debug("\tUpdated room:{}", collabRoom.toString());
            }
        }

        LOG.debug("Created collabroom for posting: \n{}", collabRoom.toString());

        return collabRoom;
    }

    /**
     * Checks if a room with the given name exists on the specified Incident
     *
     * @param incidentId ID of the incident to check
     * @param name name of the room to check
     *
     * @return true if room exists, false if not, TODO: throw exception if failure
     *
     */
    public boolean roomExists(int incidentId, String name) {

        /* TODO: apparently api doesn't expose this call, it's used internally when
         *  adding a room, but may warrant adding one to the API
        String roomExistsEndpoint = String.format("/collabroom/%d", incidentId);
        ResteasyClient client = (ResteasyClient) ClientBuilder.newClient();
        ResteasyWebTarget target = client.target(roomExistsEndpoint);
        Response response = target.request().accept(MediaType.APPLICATION_JSON)
                .header(identityHeader, identityUser).get();*/

        return false;
    }


    // Getters and Setters

    public String getLog4jPropertyFile() {
        return log4jPropertyFile;
    }

    public void setLog4jPropertyFile(String log4jPropertyFile) {
        this.log4jPropertyFile = log4jPropertyFile;
    }

    public String getIdentityHeader() {
        return identityHeader;
    }

    public void setIdentityHeader(String identityHeader) {
        this.identityHeader = identityHeader;
    }

    public String getIdentityUser() {
        return identityUser;
    }

    public void setIdentityUser(String identityUser) {
        this.identityUser = identityUser;
    }

    public int getIdentityOrgId() {
        return identityOrgId;
    }

    public void setIdentityOrgId(int identityOrgId) {
        this.identityOrgId = identityOrgId;
    }

    public String getEmapi() {
        return emapi;
    }

    public void setEmapi(String emapi) {
        this.emapi = emapi;
    }

    public String getRoomsConfig() {
        return roomsConfig;
    }

    public void setRoomsConfig(String roomsConfig) {
        this.roomsConfig = roomsConfig;
    }

    public String getIncidentAddedTopic() {
        return incidentAddedTopic;
    }

    public void setIncidentAddedTopic(String incidentAddedTopic) {
        this.incidentAddedTopic = incidentAddedTopic;
    }

    public String getIncidentAddedTopicSuper() {
        return incidentAddedTopicSuper;
    }

    public void setIncidentAddedTopicSuper(String incidentAddedTopicSuper) {
        this.incidentAddedTopicSuper = incidentAddedTopicSuper;
    }

    public String getIncidentUpdatedTopic() {
        return incidentUpdatedTopic;
    }

    public void setIncidentUpdatedTopic(String incidentUpdatedTopic) {
        this.incidentUpdatedTopic = incidentUpdatedTopic;
    }

    public String getIncidentOrgAddedTopic() {
        return incidentOrgAddedTopic;
    }

    public void setIncidentOrgAddedTopic(String incidentOrgAddedTopic) {
        this.incidentOrgAddedTopic = incidentOrgAddedTopic;
    }

    public String getIncidentAddedPattern() {
        return incidentAddedPattern;
    }

    public void setIncidentAddedPattern(String incidentAddedPattern) {
        this.incidentAddedPattern = incidentAddedPattern;
    }

    public String getIncidentAddedPatternSuper() {
        return incidentAddedPatternSuper;
    }

    public void setIncidentAddedPatternSuper(String incidentAddedPatternSuper) {
        this.incidentAddedPatternSuper = incidentAddedPatternSuper;
    }

    public String getIncidentUpdatedPattern() {
        return incidentUpdatedPattern;
    }

    public void setIncidentUpdatedPattern(String incidentUpdatedPattern) {
        this.incidentUpdatedPattern = incidentUpdatedPattern;
    }

    public String getIncidentOrgAddedPattern() {
        return incidentOrgAddedPattern;
    }

    public void setIncidentOrgAddedPattern(String incidentOrgAddedPattern) {
        this.incidentOrgAddedPattern = incidentOrgAddedPattern;
    }

    public boolean isCreateRoomsRegardlessOfRegistration() {
        return createRoomsRegardlessOfRegistration;
    }

    public void setCreateRoomsRegardlessOfRegistration(boolean createRoomsRegardlessOfRegistration) {
        this.createRoomsRegardlessOfRegistration = createRoomsRegardlessOfRegistration;
    }

    public static ConcurrentHashMap<Integer, JSONObject> getAllOrgIdToOrgMap() {
        return allOrgIdToOrgMap;
    }
}