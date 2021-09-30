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
package edu.mit.ll.nics.processor.jsonpliconsumer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Processes OCFA AVL Feed
 *
 * @author jp
 */
public class OCFAProcessor implements Processor {

    // Keys
    public static final String BATTALION = "Battalion";
    public static final String CALL_NUMBER = "CallNumber";
    public static final String DESCRIPTION = "Description";
    public static final String DIVISION = "Division";
    public static final String FLEET_ID = "FleetId";
    public static final String HEADING = "Heading";
    public static final String TIME = "LastUpdatedDateTime";
    public static final String TIME_STRING = "LastUpdatedDateTimeAsString";
    public static final String LATITUDE = "Latitude";
    public static final String LONGITUDE = "Longitude";
    public static final String SPEED = "Speed";
    public static final String STATION = "Station";
    public static final String STATUS = "Status";
    public static final String UNIT_ID = "UnitId";

    public static final String[] extended = {
            BATTALION,
            CALL_NUMBER,
            DIVISION,
            STATION,
            STATUS,
            UNIT_ID
    };

    private static Logger log;

    /**
     * Producer for sending messages to an endpoint
     */
    private static ProducerTemplate producer;

    private String nicsSchemaLocationURI;
    private String log4jPropertyFile;
    private String typeName;
    private String wfsServiceURI;
    private String wfsSchemasURI;
    private String srsName;
    private String geodatafeedConsumer;
    private boolean useSpecifiedTimezone;
    private String timezoneString;
    private boolean prependOrgToName;
    private String orgName;


    /**
     * Called by Spring once properties have been set
     */
    public void init() {
        log = Logger.getLogger(OCFAProcessor.class);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String strJson = exchange.getIn().getBody(String.class);

        if(strJson == null) {
            return;
        }

        log.info("\nGOT JSON: " + strJson + "\n");

        JSONArray jsonArr = null;
        JSONObject jsonObj = null;

        try {
            //jsonObj = new JSONObject(strJson);
            jsonArr = new JSONArray(strJson);
            //jsonObj = jsonArr.getJSONObject(0);
        } catch(JSONException e) {
            log.error("Exception reading in JSON", e);
            return;
        }

        JSONPLIEntry pliEntry = null;

        for(int i = 0; i < jsonArr.length(); i++) {

            jsonObj = jsonArr.getJSONObject(i);
            pliEntry = new JSONPLIEntry();

            int heading = jsonObj.optInt(HEADING);
            pliEntry.setCourse(heading + "");

            double lat = jsonObj.optDouble(LATITUDE);
            double lon = jsonObj.optDouble(LONGITUDE);

            pliEntry.setCoordinates(lat + "," + lon); // TODO: Make order configurable?

            String id = jsonObj.optString(FLEET_ID, null);

            if(id == null || id.isEmpty()) {
                // TODO: No ID... drop track?
                log.error("No ID was specified");
            }
            // TODO: chance to add configurable ORG to prepend
            pliEntry.setId(id);

            if(prependOrgToName && orgName != null && orgName != "") {
                pliEntry.setName(orgName + "-" + id);
            } else {
                pliEntry.setName(id);
            }

            pliEntry.setSpeed(jsonObj.optInt(SPEED) + "");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat sdfUtc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            sdfUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
            sdf.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            Date date = null;
            try {
                date = sdf.parse(jsonObj.optString(TIME_STRING));
                pliEntry.setTimestamp(sdfUtc.format(date));
            } catch(Exception e) {
                log.error("Exception parsing timestamp for ID " +
                        pliEntry.getId() + ": " + e.getMessage(), e);
            }

            String description = jsonObj.optString(DESCRIPTION, "<br/>");

            StringBuilder sb = new StringBuilder();
            sb.append(description);
            sb.append("<br/>");

            JSONObject jsonExtended = new JSONObject();

            Object objKeyVal = null;
            String strKeyVal = null;
            for(String key : extended) {

                objKeyVal = jsonObj.get(key);
                log.debug("Extended: " + key + ":" + objKeyVal);

                if(objKeyVal == null) {
                    strKeyVal = "";
                } else {

                    strKeyVal = objKeyVal + "";
                    if(strKeyVal.contains("null")) {
                        strKeyVal = "";
                    }
                }
                //jsonExtended.append(key, strKeyVal);
                jsonExtended.put(key, strKeyVal);
            }
            log.debug("Setting extended to:\n" + jsonExtended.toString());
            pliEntry.setExtended(jsonExtended.toString());

            pliEntry.setSrsName(srsName);
            pliEntry.setVersion("0.0.1");
            pliEntry.setTypeName(typeName);
            pliEntry.setNicsSchemaLocationURI(nicsSchemaLocationURI);
            pliEntry.setWfsServiceURI(wfsServiceURI);
            pliEntry.setWfsSchemasURI(wfsSchemasURI);

            log.info("\n====\n" + pliEntry.toXML(true) + "\n====\n");

            if(producer == null) {
                initProducer(exchange.getContext());
            }

            sendToEndpoint(geodatafeedConsumer, pliEntry.toXML(false));

        }
    }

    /**
     * Initializes a producer for use in this Processor
     *
     * @param context The current camel context
     */
    private void initProducer(CamelContext context) {

        if(producer != null) {
            log.info("Producer is already initialized!  Not overwriting!");
            return;
        }

        producer = context.createProducerTemplate();
    }

    /**
     * Intended to send the XML message to the endpoint that consumes and transforms into GML. Relies on producer
     *
     * <p>TODO: May prefer this to happen in the xml route?</p>
     *
     * @param endpoint
     * @param message
     * @return
     */
    private boolean sendToEndpoint(String endpoint, String message) {
        boolean success = false;

        log.debug("Sending below message to endpoint: " + endpoint
                + "\n====BODY====\n" + message + "\n============\n");

        try {
            producer.sendBody(endpoint, message);
            success = true;
        } catch(Exception e) {
            log.error("Caught unhandled exception while sending message with producer to endpoint: "
                    + endpoint + "\nError: " + e.getMessage(), e);
        }

        return success;
    }

    public String getNicsSchemaLocationURI() {
        return nicsSchemaLocationURI;
    }

    public void setNicsSchemaLocationURI(String nicsSchemaLocationURI) {
        this.nicsSchemaLocationURI = nicsSchemaLocationURI;
    }

    public String getLog4jPropertyFile() {
        return log4jPropertyFile;
    }

    public void setLog4jPropertyFile(String log4jPropertyFile) {
        this.log4jPropertyFile = log4jPropertyFile;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getWfsServiceURI() {
        return wfsServiceURI;
    }

    public void setWfsServiceURI(String wfsServiceURI) {
        this.wfsServiceURI = wfsServiceURI;
    }

    public String getWfsSchemasURI() {
        return wfsSchemasURI;
    }

    public void setWfsSchemasURI(String wfsSchemasURI) {
        this.wfsSchemasURI = wfsSchemasURI;
    }

    public String getSrsName() {
        return srsName;
    }

    public void setSrsName(String srsName) {
        this.srsName = srsName;
    }

    public String getGeodatafeedConsumer() {
        return geodatafeedConsumer;
    }

    public void setGeodatafeedConsumer(String geodatafeedConsumer) {
        this.geodatafeedConsumer = geodatafeedConsumer;
    }

    public boolean isUseSpecifiedTimezone() {
        return useSpecifiedTimezone;
    }

    public void setUseSpecifiedTimezone(boolean useSpecifiedTimezone) {
        this.useSpecifiedTimezone = useSpecifiedTimezone;
    }

    public String getTimezoneString() {
        return timezoneString;
    }

    public void setTimezoneString(String timezoneString) {
        this.timezoneString = timezoneString;
    }

    public boolean isPrependOrgToName() {
        return prependOrgToName;
    }

    public void setPrependOrgToName(boolean prependOrgToName) {
        this.prependOrgToName = prependOrgToName;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

}
