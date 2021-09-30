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

import com.vividsolutions.jts.geom.Envelope;
import edu.mit.ll.nics.common.entity.CollabRoom;
import edu.mit.ll.nics.common.entity.Incident;
import edu.mit.ll.nics.common.geoserver.api.GeoServer;
import java.util.ArrayList;
import java.util.List;

/**
 * CollabFeedGeoserver extends the Geoserver class from the geoserver rest api. Compounds a variety of geoserver methods
 * for collab feed specific functions.
 *
 * @author LE22005
 */
public class CollabFeedGeoserver extends GeoServer {

    private String workspaceName; //The Geoserver workspace to use, this should be unique per NICS server instancve
    private String dataStoreName; //The datastore used to store NICS incident and room information (the SACORE database)
    public static int SRID = 3857;
    public static String SRS_STRING = "EPSG:3857";
    //Initialize the max extent of the projection {minx maxx miny maxy}
    //Layer extents are hard coded to the extents of the USA so the bounds don't need be repeatedly updated
    public Envelope maxExtent = new Envelope(-14084454.868, -6624200.909, 1593579.354, 6338790.069);
    public Envelope maxExtentLatLon = new Envelope(-126.523, -59.506, 14.169, 49.375);

    /**
     * Constructor for CollabFeedGeoserver
     *
     * @param url           The web URL for the geoserver instance to connect to
     * @param username      for the Rest interface
     * @param password      for the Rest interface
     * @param workspaceName The Geoserver workspace to use, this should be unique per NICS server instancve
     * @param dataStoreName The datastore used to store NICS incident and room information (the SACORE database)
     */
    public CollabFeedGeoserver(String url, String username, String password, String workspaceName,
                               String dataStoreName) {
        super(url, username, password);
        this.workspaceName = workspaceName;
        this.dataStoreName = dataStoreName;
    }

    /**
     * Adds a SQL view layer of an incident view (polygons representing the bounds of the collab rooms) to the
     * geoserver
     *
     * @param incident Incident entity for the incident to be added
     * @return success of adding layer to geoserver
     */
    public boolean addIncidentView(Incident incident) {
        return this.addIncidentView(incident.getIncidentname(), incident.getIncidentid());
    }

    /**
     * Add a SQL view layer of an incident view (polygons representing the bounds of the collab rooms) to the geoserver
     *
     * @param incidentName name of the incident to be added
     * @param incidentId   id of the incident to be added
     * @return
     */
    public boolean addIncidentView(String incidentName, int incidentId) {
        String layerName = "I" + String.valueOf(incidentId);
        if(this.addFeatureTypeSQL(workspaceName, dataStoreName, layerName, SRS_STRING,
                "SELECT * FROM collabroom where incidentid=" + incidentId, "bounds", "Geometry", SRID)) {
            this.updateLayerStyle(layerName, workspaceName, "incidentOverviewStyle");
            this.updateFeatureTypeTitle(layerName, workspaceName, dataStoreName, incidentName);
            this.updateFeatureTypeBounds(workspaceName, dataStoreName, layerName, maxExtent, maxExtentLatLon,
                    SRS_STRING);
            this.updateFeatureTypeEnabled(workspaceName, dataStoreName, layerName, true);
            this.updateLayerEnabled(layerName, workspaceName, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a SQL View layer of the collaboration room
     *
     * @param room Room entity for the room to be added
     * @return success of adding layer to geoserver
     */
    public boolean addCollabRoomView(CollabRoom room) {
        return this
                .addCollabRoomView(room.getIncident().getIncidentname() + "-" + room.getName(), room.getCollabRoomId());
    }

    /**
     * Adds a SQL View layer of the collaboration room with the "collabRoomStyle"
     *
     * @param roomName name of room being added
     * @param roomId   Id of room being added
     * @return success of adding layer to geoserver
     */
    public boolean addCollabRoomView(String title, int roomId) {
        String layerName = "R" + String.valueOf(roomId);
        if(this.addFeatureTypeSQL(workspaceName, dataStoreName, layerName, SRS_STRING,
                "SELECT f.* from Feature f, CollabroomFeature cf WHERE cf.featureid=f.featureid and cf.collabroomid=" +
                        roomId + " and deleted='f'",
                "the_geom", "Geometry", SRID)) {
            this.updateLayerStyle(layerName, workspaceName, "collabRoomStyle");
            this.updateFeatureTypeTitle(layerName, workspaceName, dataStoreName, title);
            this.updateFeatureTypeBounds(workspaceName, dataStoreName, layerName, maxExtent, maxExtentLatLon,
                    SRS_STRING);
            this.updateFeatureTypeEnabled(workspaceName, dataStoreName, layerName, true);
            this.updateLayerEnabled(layerName, workspaceName, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the list of current layers in the workspace and datastore
     *
     * @return list of layers/featuretypes
     */
    public List<String> getFeatureTypeList() {
        List<String> list = this.getFeatureTypeList(workspaceName, dataStoreName);
        if(list == null) {
            list = new ArrayList<String>();
        }
        return list;
    }

    /**
     * Adds a new SQL view layer for a user's MyMaps room.
     */
    public boolean addMyMapsView(int userId) {
        final String query =
                "SELECT f.* from Feature f, UserFeature uf WHERE uf.featureid=f.featureid and uf.userid=" + userId +
                        " and deleted='f'";
        final String featName = userId + "_MyMap";
        if(this.addFeatureTypeSQL(workspaceName, dataStoreName, featName, SRS_STRING, query, "the_geom", "Geometry",
                SRID)) {
            this.updateLayerStyle(featName, workspaceName, "collabRoomStyle");
            this.updateFeatureTypeBounds(workspaceName, dataStoreName, featName, maxExtent, maxExtentLatLon,
                    SRS_STRING);
            this.updateFeatureTypeEnabled(workspaceName, dataStoreName, featName, true);
            this.updateLayerEnabled(featName, workspaceName, true);

            return true;
        }

        return false;
    }
}
