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
/**
 * Working notes: 14Mar2013/Ted/next: Modify the json element names capture to fit Ventura
 */

package edu.mit.ll.nics.processor.jsonpliconsumer;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Processes NICS JSON PLI as specified in our NICS Guidance for PLI document. 
 *
 * Example data:
 *
 * {"ID":"info:us.ma.mit.ll.nics/pli.json.v1:us.ca.calfire.rru/E5","Name":"E5","Description":null,
 * "Point":"-117.90754,+34.02992","Course":"181","Speed":"",
 * "Extended":{"Organization":"WESTCOV","Incident":null,"Role":null},
 * "Timestamp":"2014-04-01T08:20:25","Version":"0.0.1"}
 *
 */
public class JSONPLIProcessor implements Processor {

    /**
     * Logger
     */
    private static Logger LOG;

    private static final String DEFAULT_PROCESSOR = "JSONPLIProcessor";
    private static final String OCFA_PROCESSOR = "OCFAProcessor";


    /**
     * Available processor classes to choose from. For use with 'processorClass' property.<br/>
     * Default: JSONPLIProcessor
     */
    private static final String[] processors = {DEFAULT_PROCESSOR, OCFA_PROCESSOR};

    /**
     * Producer for sending messages to an endpoint
     */
    private static ProducerTemplate producer;

    private static String TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    private static SimpleDateFormat SDF = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US);

    private static String sourceFileEncoding = "UTF-16";
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
    private String processorClass = JSONPLIProcessor.class.getSimpleName();

    private OCFAProcessor ocfaProcessor;

    private boolean valid = false;

    /**
     * Default constructor
     */
    public JSONPLIProcessor() {
    }

    /**
     * init method, specified by Spring bean configuration 'init-method'.
     * <p>Note: this is called after instantiation, and after properties been
     * set in the spring lifecycle
     * </p>
     */
    @SuppressWarnings("unused")
    private void init() {

        try {
            PropertyConfigurator.configure(log4jPropertyFile);
            LOG = Logger.getLogger(JSONPLIProcessor.class);
        } catch(Exception e) {
            LOG.error("Caught unhandled exception while attemtping to read log4j property file ("
                    + log4jPropertyFile + "): " + e.getMessage(), e);
        }

        //clock = new TimeUTC();  // initialize clock

        validateProcessor();

        LOG.info("\n!!! Finished " + JSONPLIProcessor.class.getSimpleName() + " initialization!!!\n");
        LOG.info("\n!!! using geodatafeedConsumer endpoint: " + this.geodatafeedConsumer + "\n\n");
    }

    /**
     * Processes incoming PLI message from Ventura County CA Fire Dept.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        LOG.info("\n\nVALID?: " + valid);
        if(!valid) {
            LOG.fatal("Invalid/missing properties!");
            exchange.getContext().stop();
        }

        LOG.debug("In process!");

        if(processorClass.equals(OCFA_PROCESSOR)) {
            ocfaProcessor.process(exchange);
            return;
        }

        if(producer == null) {
            initProducer(exchange.getContext());
        }

        try {

            // Get the the incoming text message, with multiple PLI from Ventura
            String venturaMsg = exchange.getIn().getBody(String.class);
            LOG.debug("JSON PLI Msg ===" + venturaMsg);

            if(venturaMsg == null || venturaMsg.isEmpty()) {
                LOG.info("Not processing, message is null");
                return;
            }

            // transform incoming test to a JSON object, then to a JSON array
            JSONObject pliObj;

            try {
                pliObj = new JSONObject(venturaMsg); // create obj 1st

            } catch(JSONException e) {
                LOG.error("JSON exception initializing JSON track, processing abandoned\n" + e.getMessage());
                return;
            }

            // Sample current time
            Date date = new Date();
            SDF.setTimeZone(TimeZone.getTimeZone("UTC"));
            String timestamp = SDF.format(date);

            // Initialize a bit of state to protect against assumed data,
            // such as always having lat and lon (want both or none)            
            boolean haveLat = false, haveLon = false;


            // Enable skipping messages that do not have a valid data set
            boolean invalidData = false;
            String invalidDataInfo = "Invalid Data:\n";


            // Create a new object for the new 
            JSONPLIEntry pliEntry = new JSONPLIEntry();

            // Create defaults for necessary information items
            pliEntry.setSrsName(srsName);  // default reference system               

            Iterator itr = pliObj.keys();
            String lat = null, lon = null, unit = null;
            while(itr.hasNext()) {

                /** ====== REPLACE WITH Ventura PARAMS inc timestamp */
                String element = (String) itr.next();
                String value;
                LOG.debug("Reading element key: " + element);
                try {
                    if(element != null && !element.isEmpty()) {
                        value = pliObj.getString(element);
                    } else {
                        LOG.debug("Invalid key, not processing this element");
                        continue;
                    }
                } catch(JSONException e) {
                    LOG.error("Exception getting value for key, not processing element: " + element);
                    continue;
                }

                LOG.debug("Element value: " + value);

                if("id".equalsIgnoreCase(element)) {
                    pliEntry.setId(value);
                } else if("name".equalsIgnoreCase(element)) {
                    pliEntry.setName(value);
                } else if("unit_name".equalsIgnoreCase(element)) { // Ventura Specific
                    pliEntry.setName(value);
                    unit = value;
                } else if("current_lat".equalsIgnoreCase(element)) { // Ventura Specific
                    lat = value;
                } else if("current_lon".equalsIgnoreCase(element)) { // Ventura Specific
                    lon = value;
                } else if("description".equalsIgnoreCase(element)) {
                    pliEntry.setDescription(value);
                } else if("point".equalsIgnoreCase(element)) {
                    // HACK to flip lon/lat to lat/lon
                    String coords = null;
                    if(value != null) {
                        String[] parts = value.split(",");
                        if(parts.length >= 2) {
                            coords = parts[1] + "," + parts[0];
                        } else {
                            invalidData = true;
                            invalidDataInfo += element + ": " + value + "\n";
                        }
                    }
                    pliEntry.setCoordinates(coords);
                    //venturaEntry.setCoordinates(value);
                } else if("srsName".equalsIgnoreCase(element)) {
                    pliEntry.setSrsName(value);
                } else if("coordinates".equalsIgnoreCase(element)) {
                    pliEntry.setCoordinates(value);
                } else if("speed".equalsIgnoreCase(element)) {
                    // TODO: Hack to make speed a double
                    if(value != null && !value.isEmpty() && !value.contains(".")) {
                        value = value + ".0";
                    }
                    pliEntry.setSpeed(value);
                } else if("course".equalsIgnoreCase(element)) {
                    pliEntry.setCourse(value);
                } else if("extended".equalsIgnoreCase(element)) {
                    pliEntry.setExtended(value);
                } else if("timestamp".equalsIgnoreCase(element)) {

                    //DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); //.parse(value);
                    // Workaround for if sender isn't giving us time in UTC
                    //if(useSpecifiedTimezone && timezoneString != null && !timezoneString.isEmpty()) {

                    //	dateFormat.setTimeZone(TimeZone.getTimeZone(timezoneString));
                    //}
                    //Date date = dateFormat.parse(value);
                    //pliEntry.setTimestamp(date.toGMTString()); // TODO: stop using deprecated toGMT and format to UTC
                    pliEntry.setTimestamp(value);
                } else if("version".equalsIgnoreCase(element)) {
                    pliEntry.setVersion(value);
                } // end if
            } // end while

            boolean ventura = false; // TODO: make this configurable
            if(ventura) {
                // TODO: ventura specific
                //String trackId = "info:us.ma.mit.ll.nics/pli.json.v1:us.ca.ventura/" + unit;
                String trackId = "info:us.ma.mit.ll.nics/Ventura:us.ca.ventura/" + unit;
                pliEntry.setId(trackId);

                // TODO: set coordinates
                //String coordinates = lon + "," + lat;
                String coordinates = lat + "," + lon; // geodatafeed-consumer wants it backward?
                if(lon == null || lat == null) {
                    invalidData = true;
                    invalidDataInfo += "lon,lat:" + lon + "," + lat + "\n";
                }
                pliEntry.setCoordinates(coordinates);

                // Set the sampled timestamp from when process was entered, since Ventura is not
                // providing a timestamp
                pliEntry.setTimestamp(timestamp);

                // description, speed, course, extended, version...
                pliEntry.setDescription(
                        pliEntry.getDescription() == null ? "Ventura - " + unit : pliEntry.getDescription());

            } else {
                // Check nulls and do other processing

                // Process adding of ORG to name if property turned on
                if(prependOrgToName) {
                    pliEntry.setName(getOrgPrependedName(pliEntry.getId(), pliEntry.getName()));
                }

                // Process Description. the PLIEntry toXML method handles appending
                // extended data
                if(pliEntry.getDescription() == null) {
                    pliEntry.setDescription("");
                }
            }

            pliEntry.setSpeed(
                    (pliEntry.getSpeed() == null || pliEntry.getSpeed().isEmpty()) ? "0.0" : pliEntry.getSpeed());
            pliEntry.setCourse((pliEntry.getCourse() == null) ? "0" : pliEntry.getCourse());
            pliEntry.setVersion("0.0.1");

            pliEntry.setTypeName(typeName);
            pliEntry.setNicsSchemaLocationURI(nicsSchemaLocationURI);
            pliEntry.setWfsServiceURI(wfsServiceURI);
            pliEntry.setWfsSchemasURI(wfsSchemasURI);

            if(!invalidData) {
                String gmlString = pliEntry.toXML(false);
                LOG.debug("\n\n!!!Sending GML: \n" + gmlString + "\n\n");
                sendToEndpoint(geodatafeedConsumer, pliEntry.toXML(false));
            } else {
                LOG.warn("\nNOT sending track due to invalid data being included: " + invalidDataInfo);
            }

        } catch(Exception e) {
            LOG.error("Caught unhandled exception in process(): " + e.getMessage(), e);
        }
    }

    /**
     * Processes the ORG out of the ID string, and prepends it to the name if
     * the name field doesn't already have the ORG prepended
     *
     * @param id
     * @param name
     * @return A String in the form "ORG - NAME" if the ID is valid, otherwise the original name
     * 	 	   parameter is returned
     */
    private String getOrgPrependedName(String id, String name) {
        // example: info:us.ma.mit.ll.nics/pli.json.v1:us.ca.FCO/E73

        String orgName = name;

        if(id == null || id.isEmpty() || name == null) {
            LOG.info("INVALID/NULL ID or NAME, can't process for prepending!");
            return name;
        }

        String[] parts = id.split(":");
        LOG.debug("ID parts: " + Arrays.toString(parts));

        if(parts == null || parts.length != 3) {
            LOG.info("INVALID ID, can't process ORG for appending to name");
            return name;
        }

        String org = null;

        String endOrgUnitParts[] = parts[2].split("/");
        LOG.debug("ID end parts: " + ((endOrgUnitParts != null) ? Arrays.toString(endOrgUnitParts) : "null"));

        if(endOrgUnitParts != null && endOrgUnitParts.length == 2) {
            String orgpart = endOrgUnitParts[0];
            org = orgpart.substring(orgpart.lastIndexOf(".") + 1, orgpart.length());

            String namepart = endOrgUnitParts[1];

            // Name already contains the org
            if(!name.toLowerCase().contains(org.toLowerCase())) {

                orgName = org + " - " + name;

                if(!namepart.toLowerCase().equals(name.toLowerCase())) {
                    // warning, names differ
                    LOG.warn("Name differs from name in ID field: \n" +
                            "\tName: " + name + "\n\tID unit: " + namepart + "\n");
                }
            } else {
                LOG.debug("Not prepending ORG, it's already in the name: " + name);
            }
        } else {
            LOG.warn("Unexpected number of parts in end of ID: <org hierarchy>/<UNITID>, not prepending ORG to NAME");
        }


        return orgName;
    }

    /**
     * Initializes a producer for use in this Processor
     *
     * @param context The current camel context
     */
    private void initProducer(CamelContext context) {

        if(producer != null) {
            LOG.info("Producer is already initialized!  Not overwriting!");
            return;
        }

        producer = context.createProducerTemplate();
    }

    /**
     * Intended to send the XML message to the endpoint that consumes and
     * transforms into GML. Relies on producer
     *
     * <p>TODO: May prefer this to happen in the xml route?</p>
     *
     * @param endpoint
     * @param message
     * @return
     */
    private boolean sendToEndpoint(String endpoint, String message) {
        boolean success = false;

        LOG.debug("Sending below message to endpoint: " + endpoint
                + "\n====BODY====\n" + message + "\n============\n");

        try {
            producer.sendBody(endpoint, message);
            success = true;
        } catch(Exception e) {
            LOG.error("Caught unhandled exception while sending message with producer to endpoint: "
                    + endpoint + "\nError: " + e.getMessage(), e);
        }

        return success;
    }

    private void validateProcessor() {
        //boolean valid = false;
        if(processorClass == null || processorClass.isEmpty()) {
            LOG.fatal("processorClass property not specified. Cannot continue execution.");
            System.exit(1); // TODO: what's proper way to exit processor/application?
        }

        for(String proc : processors) {
            if(proc.toLowerCase().equals(processorClass.toLowerCase())) {
                LOG.info("Using processor: " + processorClass);
                valid = true;
            }
        }

        if(!valid) {
            LOG.fatal("No valid processor class specified in 'processorClass' property.");
            System.exit(1);
        }

    }

    public static String getSourceFileEncoding() {
        return sourceFileEncoding;
    }

    public static void setSourceFileEncoding(String sourceFileEncoding) {
        JSONPLIProcessor.sourceFileEncoding = sourceFileEncoding;
    }

    public void setNicsSchemaLocationURI(String nicsSchemaLocationURI) {
        this.nicsSchemaLocationURI = nicsSchemaLocationURI;
    }

    public String getNicsSchemaLocationURI() {
        return nicsSchemaLocationURI;
    }

    public void setLog4jPropertyFile(String log4jPropertyFile) {
        this.log4jPropertyFile = log4jPropertyFile;
    }

    public String getLog4jPropertyFile() {
        return log4jPropertyFile;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setWfsServiceURI(String wfsServiceURI) {
        this.wfsServiceURI = wfsServiceURI;
    }

    public String getWfsServiceURI() {
        return wfsServiceURI;
    }

    public void setWfsSchemasURI(String wfsSchemasURI) {
        this.wfsSchemasURI = wfsSchemasURI;
    }

    public String getWfsSchemasURI() {
        return wfsSchemasURI;
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

    public String getProcessorClass() {
        return processorClass;
    }

    public void setProcessorClass(String processorClass) {
        this.processorClass = processorClass;
    }

    public OCFAProcessor getOcfaProcessor() {
        return ocfaProcessor;
    }

    public void setOcfaProcessor(OCFAProcessor ocfaProcessor) {
        this.ocfaProcessor = ocfaProcessor;
    }
}
