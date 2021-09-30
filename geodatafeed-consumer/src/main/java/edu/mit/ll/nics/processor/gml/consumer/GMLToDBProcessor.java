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
package edu.mit.ll.nics.processor.gml.consumer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang.time.StopWatch;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.geotools.GML;
import org.geotools.GML.Version;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.type.Types;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;


public class GMLToDBProcessor implements Processor {

    /**
     * The logger.
     */
    private static final Logger log = Logger.getLogger(GMLToDBProcessor.class.getSimpleName());

    private static boolean hasInitialized = false;

    // !!! Properties !!!

    /**
     * The type of the database this processor connects to. Currently only supports 'postgis'
     */
    private String dbtype = "postgis";

    /**
     * Host/IP of the database to connect to
     */
    private String dbhost;

    /**
     * Database port to connect to.
     */
    private int dbport;

    /**
     * Name of the database to use
     */
    private String dbname;

    /**
     * Username used to access the database
     */
    private String dbuser;

    /**
     * Password used to access the database
     */
    private String dbpassword;

    /**
     * CRS value used for the incoming GIS data
     */
    private String crs;

    /**
     * DataSource reset interval in minutes
     */
    private long db_reset_interval;

    /**
     * The property name that contains the time in the GML Default: timestamp
     */
    private String timestampPropertyName = "timestamp";

    /**
     * The SimpleDateFormat pattern expected from the timestamp field Default: yyyy-MM-dd'T'HH:mm:ssXXX
     */
    private String dateFormatPatternOp0 = "yyyy-MM-dd'T'HH:mm:ss";
    private String dateFormatPatternOp1 = "yyyy-MM-dd'T'HH:mm:ssX";
    private String dateFormatPatternOp2 = "yyyy-MM-dd'T'HH:mm:ssXX";
    private String dateFormatPatternOp3 = "yyyy-MM-dd'T'HH:mm:ssXXX";


    private String log4jPropertyFile;

    /**
     * GML Version to use.
     * <p>Options:
     * 		<ul>
     * 			<li>WSF1_0</li>
     * 			<li>WSF1_1</li>
     * 			<li>GML2</li>
     * 			<li>GML3</li>
     * 		</ul>
     * </p>
     * <p>Default: WSF1_0</li>
     */
    private String gml_version;


    // !!! Local private use objects !!!

    /**
     * The GeoTools datastore object which connects to the database specified in the db_params Map
     */
    private static DataStore datastore = null;

    /**
     * The coordinate reference system used for a table
     */
    private static CoordinateReferenceSystem tablecrs = null;

    /**
     * Unique identifier field for lookup of incoming features in the db
     */
    private static final String id_table_entry = "id"; // TODO: Make a property?

    /**
     * GML object used for processing incoming GML messages
     */
    private static GML gml;

    /**
     * Map of database parameters for use with the GeoTools DataStore object
     */
    private static Map db_params;

    /**
     * Interval in milliseconds to reset the datastore
     */
    private static long datastore_reset_interval;

    /**
     * TODO:
     */
    private static final StopWatch stopwatch = new StopWatch();

    /**
     * TODO:
     */
    private static String last_table;

    /**
     * String of bad GML coordinates containing only a comma
     */
    private static final String gml_coord_comma = "<gml:coordinates>,</gml:coordinates>";

    /**
     * Number of messages that have entered the process, used for debugging
     */
    private static int num_msg_started = 0;

    /**
     * Number of messages that have had an exception, used for debugging
     */
    private static int num_msg_ex = 0;

    /**
     * Number of messages that have had a handled exception, used for debugging
     */
    private static int num_msg_h_ex = 0;

    /**
     * Time threshold to throw away incoming features if they are older than
     */
    private static long old_feature_threshold = 12 * 3600 * 1000;

    /**
     * Time threshold to throw away incoming features if they are newer than (i.e. in the future)
     */
    private static long new_feature_threshold = 12 * 3600 * 1000;

    /**
     * Public constructor
     */
    public GMLToDBProcessor() {
    }

    /**
     * Initializes datastore
     *
     * @return true if datastore was successfully initialized, false otherwise
     */
    private boolean init_datastore() {
        boolean success = false;
        try {
            // If no datastore is found, this will be null
            datastore = DataStoreFinder.getDataStore(db_params);

            if(datastore == null) {
                log.info("datastore was null, so no datastore was found");
            } else {
                success = true;
                stopwatch.reset();
                stopwatch.start();
            }
        } catch(Exception e) {
            log.error("Unhandled exception while getting datastore: " + e.getMessage(), e);
        }

        return success;
    }


    /**
     * Disposes of the datastore connection, and reconnects
     */
    private void reset_datastore() {
        try {
            log.info("\n!!! RESETTING DATASTORE !!!\n");
            if(datastore != null) {
                datastore.dispose();
                datastore = null;
            }

            try {
                datastore = DataStoreFinder.getDataStore(db_params);
            } catch(IOException dse) {
                log.error("Unhandled Exception (" + dse.getMessage() + ") getting datastore with db_params: "
                        + db_params.toString(), dse);
            }


            if(datastore == null) {
                log.fatal("reset_datastore(): Could not connect to PostGIS DB");
                System.exit(1);
            } else {
                log.info("Successfully connected PostGIS DB");
                stopwatch.reset();
                stopwatch.start();
            }

        } catch(Exception e) {
            log.error("Error initializing database connection.  Exiting.", e);
            System.exit(1);
        }

    }


    /**
     * Initializes various objects with specified property values
     */
    private boolean init() {
        PropertyConfigurator.configure(log4jPropertyFile);

        try {
            Logging.GEOTOOLS.setLoggerFactory("org.geotools.util.logging.Log4JLoggerFactory");
        } catch(Exception e) {
            log.error("Caught unhandled exception while setting GeoTools to use log4j" + e.getMessage(), e);
        }

        boolean success = true;

        // TODO: Validate critical properties

        db_params = new HashMap();
        db_params.put("dbtype", dbtype);
        db_params.put("host", dbhost);
        db_params.put("port", dbport);
        db_params.put("database", dbname);
        db_params.put("user", dbuser);
        db_params.put("passwd", dbpassword);

        log.info("Using the following for database:\n" + db_params.toString());

        datastore_reset_interval = db_reset_interval * 60 * 1000;
        log.info("Using a db_reset_interval of: " + db_reset_interval);

        // Initialize the datastore
        //reset_datastore();
        if(!init_datastore()) {
            success = false;
        }

        try {
            // Set the CRS
            tablecrs = CRS.decode(crs);
            log.info("Set CRS to: " + crs);
            log.info("tablecrs: " + tablecrs);
        } catch(NoSuchAuthorityCodeException e) {
            log.error("NoSuchAuthorityException while setting CoordinateReferenceSystem to: '" + crs +
                    "': " + e.getMessage(), e);
            System.exit(1);

        } catch(FactoryException e) {
            log.error("FactoryException while setting CoordinateReferenceSystem to: '" + crs +
                    "': " + e.getMessage(), e);
            System.exit(1);

        } catch(Exception e) {
            log.error("Caught unhandled exception while setting CoordinateReferenceSystem to: '" + crs +
                    "': " + e.getMessage(), e);
            System.exit(1);
        }

        // Initialize the GML object to the specified version
        gml = new GML(parseGMLVersion());

        hasInitialized = true;

        return success;
    }


    /**
     * Parses the gml_version property for the GeoTools GML Version to instantiate the GML object with.
     *
     * @return the proper Version value the gml_version maps to.  If the one specified is not found, a default of WFS1_0
     * is returned.
     */
    private Version parseGMLVersion() {
		/* Options:
	  		<li>WSF1_0</li>
  			<li>WSF1_1</li>
  			<li>GML2</li>
  			<li>GML3</li>
		 */

        Version version = null;
        try {
            version = Version.valueOf(gml_version);
        } catch(Exception e) {
            log.info("Unhandled Exception while getting GML Version to use from gml_version property. " +
                    "Passed in value (" + gml_version + ") most likely didn't match one of the acceptable " +
                    "values (WFS1_0, WFS1_1, GML2, GML3).");
        } finally {
            if(version == null) {
                log.info("GML Version was unable to be processed from property, setting to default value of: WFS1_0");
                version = Version.WFS1_0; // the default
            } else {
                log.info("Parsed GMLVersion to: " + version.name());
            }
        }

        return version;
    }


    /**
     * Processes incoming GML messages, and inserts them into a PostGIS database
     *
     * @param exchange The incoming exchange from the camel route
     */
    @Override
    //@SuppressWarnings({"unchecked", "unchecked", "unchecked", "unchecked"})
    public void process(Exchange exchange) {

        if(!hasInitialized && !init()) {
            log.info("Initialization failed... shutting down.");
            try {
                exchange.getContext().stop();
                System.exit(1);
            } catch(Exception e) {
                log.error("Unhandled exception while attempting to stop context: " + e.getMessage(), e);
            }
        }

        num_msg_started++;

        SimpleFeatureIterator iterator = null;
        SimpleFeatureCollection getfeatures = null;
        SimpleFeatureCollection featcollection = null;
        SimpleFeatureStore featStore = null;

        String id = null;
        int count;
        Filter filter = null;

        // get the GML message from the exchange
        InputStream in = exchange.getIn().getBody(InputStream.class);
        String gml_str = exchange.getIn().getBody(String.class);
        //log.info("Processing Message In : " + gml_str);

        // Test for the coordinates being ',' here...
        if(gml_str.contains(gml_coord_comma)) {
            log.info("Dropping message:\n"
                    + gml_str + "\n\nRejecting above message due to invalid coordinates");
            return;
        }

        try {
            if(stopwatch.getTime() > datastore_reset_interval) {
                stopwatch.stop();
                reset_datastore();
            }

            // Bad coordinates bomb here, so checks later don't help, at least not for the ',' kind - jp
            featcollection = gml.decodeFeatureCollection(in);
            //log.info("GML parsed");

            iterator = featcollection.features();
            SimpleFeature feat = iterator.next();

            // The below check of the attributes may be unnecessary? When it's decoded, it runs
            // into syntax issues and dies, dropping the track. However, there may be parseable values
            // that make it here, then other issues the validation below susses out. Except not sure if
            // the decoding does validation at the same time already, though? - jp

            //ERROR CHECK - Use geotools validation
            for(AttributeDescriptor property : feat.getType().getAttributeDescriptors()) {
                Object value = feat.getAttribute(property.getName());
                try {
                    Types.validate(property, value);
                } catch(IllegalAttributeException ex) {
                    throw new GdfcException("Failed geotools validation", ex);
                }
            }

            //ERROR CHECK - 1 FEATURE
            if(iterator.hasNext()) {
                throw new GdfcException("More than one feature in incoming GML");
            }

            //ERROR CHECK - ID
            try {
                id = feat.getAttribute(id_table_entry).toString();
            } catch(NullPointerException ex) {
                throw new GdfcException("NullPointerException getting " + id_table_entry + " attribute from feature",
                        ex);
            }

            //ERROR CHECK - TIMESTAMP
            // Don't persist a track with an invalid or old time
            Timestamp tsNew = getTimestampFromFeature(feat);
            if(tsNew == null) {
                throw new GdfcException("Unparseable timestamp, dropping track");
            }

            long currentTimeMillis = System.currentTimeMillis();

            if(tsNew.before(new Timestamp(currentTimeMillis - old_feature_threshold))) { //if it's older than threshold
                throw new GdfcException("Timestamp is old and being ignored");
            }

            if(tsNew.after(new Timestamp(currentTimeMillis + new_feature_threshold))) { //if it's "newer" than threshold
                throw new GdfcException("Timestamp is too far in the future and being ignored");
            }


            //ERROR CHECK - COORDINATES
            Point point = (Point) feat.getDefaultGeometry();
            if(!Pattern.matches("(\\(-?\\d+(\\.\\d+)?,\\s?-?\\d+(\\.\\d+)?(,\\s?-?\\d+(\\.\\d+)?|(,\\s?NaN))?\\))",
                    point.getCoordinate().toString())) {
                throw new GdfcException("failed coordinate regex check on: " + point.getCoordinate().toString());
            }
            if(point.getCoordinate().equals(new Coordinate(0, 0))) {
                throw new GdfcException("Coordinates 0,0 in GML");
            }

            //ERROR CHECK - COORDINATE REFERENCE SYSTEM
            String feat_crs_wkt = point.getUserData().toString();
            CoordinateReferenceSystem pointcrs = CRS.parseWKT(feat_crs_wkt);
            if(pointcrs == null) {
                throw new GdfcException("pointcrs is null");
            }

            //transform coordinates to our preferred CRS
            MathTransform transform = CRS.findMathTransform(pointcrs, tablecrs, false);
            point = (Point) JTS.transform(point, transform);
            point.setUserData(null);
            feat.setDefaultGeometry(point);


            //THIS FEATURE ISNT USED, DISABLE FOR NOW
            // Log the track to the current table's log table
            //logTrack(postgisTableName + "_log", featcollection);


            //connect to feature store
            final SimpleFeatureType schema_new = featcollection.getSchema();
            final String postgisTableName = schema_new.getName().getLocalPart();
            //featuresource is read only, cast as featurestore for write/modify
            featStore = (SimpleFeatureStore) datastore.getFeatureSource(postgisTableName);
            //log.info("connected to postgis table: " + postgisTableName);

            //look for feature in table
            filter = CQL.toFilter(id_table_entry + " = '" + id + "'");
            getfeatures = featStore.getFeatures(filter);
            count = getfeatures.size();

            switch(count) {
                case 0:    //if no feature exists then create feature for postgis database
                    //log.info("adding feature: " + id + " to db: " + postgisTableName);

                    //add feature to database
                    Transaction addTransaction = new DefaultTransaction("add");
                    featStore.setTransaction(addTransaction);
                    try {
                        featStore.addFeatures(featcollection);
                        addTransaction.commit();
                    } catch(Exception ex) {
                        addTransaction.rollback();
                        throw new GdfcException("Exception adding features: " + ex.getMessage(), ex);
                    } finally {
                        addTransaction.close();
                    }

                    //log.info("added feature: " + id + " to table: " + postgisTableName);
                    break;

                case 1://if feature is in table modify feature with updated info

                    //log.info("found feature id: " + id + " in db: " + postgisTableName);

                    // Get existing track's timestamp
                    Timestamp tsCurrent = getTimestampFromFeatureCollection(getfeatures);

                    if(tsCurrent == null) {
                        throw new GdfcException("A feature exists in the DB with a null timestamp");
                    } else if(tsNew.before(tsCurrent)) {
                        throw new GdfcException("A feature exists in the DB with a newer timestamp");
                    }

                    Transaction removeAddTransaction = new DefaultTransaction("remove_add");
                    featStore.setTransaction(removeAddTransaction);
                    try {
                        featStore.removeFeatures(filter);
                        featStore.addFeatures(featcollection);
                        removeAddTransaction.commit();
                    } catch(Exception ex) {
                        removeAddTransaction.rollback();
                        throw new GdfcException("Exception during remove_add transaction: " + ex.getMessage(), ex);
                    } finally {
                        removeAddTransaction.close();
                    }

                    //log.info("modified feature: " + id + " in table: " + postgisTableName);

                    break;

                default:
                    throw new GdfcException(
                            "More than one feature with same " + id_table_entry + " in DB, " + id_table_entry + " : " +
                                    id);
            }

            if(!postgisTableName.equals(last_table)) {
                last_table = postgisTableName;
                log.info("current data source: " + postgisTableName);
            }

        } catch(GdfcException ex) {
            log.warn("Caught expected exception processing gml: " + gml_str + " exception: " + ex.getMessage());
            num_msg_h_ex++;

        } catch(Exception ex) {
            log.error("Caught UNEXPECTED exception processing gml: " + gml_str + " exception: " + ex, ex);
            num_msg_ex++;

        } finally {
            try {
                if(num_msg_started % 1000 == 0) {
                    log.info("number of messages started: " + num_msg_started +
                            "   number of messages with a handled exception: " + num_msg_h_ex +
                            "   number of messages with an unhandled exception: " + num_msg_ex);
                }

                featStore = null;
                featcollection = null;
                getfeatures = null;

                if(iterator != null) {
                    iterator.close();
                }

                in.close();

            } catch(IOException ex) {
                log.error("could not close GML inputstream");
                num_msg_ex++;
            }
        }
    }


    /**
     * Logs the features to a log table, created by appending "_log" to the postgisTableName
     *
     * @param logTableName The name of the log table to log to
     * @param features     The feature collection to log
     * @return true if successfully committed track to log, false otherwise
     */
    private boolean logTrack(String logTableName, SimpleFeatureCollection features) {
        boolean success = false;

        if(datastore == null || logTableName == null || logTableName.isEmpty()) {
            log.warn("Required parameter(s) null, so can't connect to log table for: " + logTableName);
            return success;
        }

        // connect to logstore, assumed named with appended _log
        SimpleFeatureStore logStore = null;
        try {
            logStore = (SimpleFeatureStore) datastore.getFeatureSource(logTableName);
            Transaction logTransaction = new DefaultTransaction("add");
            logStore.setTransaction(logTransaction);
            try {
                logStore.addFeatures(features);
                logTransaction.commit();
                success = true;
            } catch(Exception ex) {
                log.error("Exception adding features: " + ex.getMessage(), ex);
                logTransaction.rollback();
            } finally {
                logTransaction.close();
            }
        } catch(IOException e) {
            log.error("IOException connecting to log table store. No log table for : " +
                    logTableName + "! Can't log feature");
        } catch(Exception e) {
            log.error("Unhandled exception while trying to connect to log table for: " +
                    logTableName, e);
        }

        return success;

    }

    /**
     * Helper method to convert a "timestamp" on a feature from a String to a Timestamp. It first tries creating a
     * Timestamp from the standard format, and failing that, attempts a more generic method of parsing the timestamp
     * String
     *
     * @param strTime Date/time string as it exists in the incoming GML track
     * @return A SQL Timestamp object set to the specified time if successful in parsing, null otherwise
     *
     * @throws GdfcException
     */
    private Timestamp getTimestampFromFeatureString(String strTime) throws GdfcException {
        Timestamp ts = null;
        java.util.Date date = null;
        SimpleDateFormat sdf = null;
        String dateFormatPattern = null;

        // Expected format... but should be forgiving if this doesn't work
        // yyyy-MM-dd'T'HH:mm:ssX   yyyy-MM-dd'T'HH:mm:ssXX   yyyy-MM-dd'T'HH:mm:ssXXX
        // can be any iso8601 option:  Z, -08; -0800; -08:00
        // ASSUME no Z or timezone info means zulu time, maybe risky

        if(strTime.length() == 19) {
            dateFormatPattern = dateFormatPatternOp0;
        } else if(strTime.length() <= 22) {
            dateFormatPattern = dateFormatPatternOp1;
        } else if(strTime.length() == 24) {
            dateFormatPattern = dateFormatPatternOp2;
        } else if(strTime.length() == 25) {
            dateFormatPattern = dateFormatPatternOp3;
        } else {
            throw new GdfcException("timestamp did not fit any of the expected formats" + strTime);
        }

        try {
            log.debug("Using this dateFormatPattern: " + dateFormatPattern);
            sdf = new SimpleDateFormat(dateFormatPattern, Locale.US); // TODO:NEW make locale configurable
            date = sdf.parse(strTime);
            ts = new Timestamp(date.getTime());
        } catch(ParseException e) {
            throw new GdfcException("Exception parsing incoming timestamp(" + strTime + "): " + e);
        }


        return ts;
    }


    /**
     * Attempts to extract the "timestamp" property out of the feature collection
     *
     * @param features collection of features assumed to contain a "timestamp" property, in this use there is only one
     *                 feature in each collection
     * @return An initialized Timestamp object if successful, null otherwise
     *
     * @throws GdfcException
     */
    private Timestamp getTimestampFromFeatureCollection(SimpleFeatureCollection features) throws GdfcException {
        final SimpleFeatureIterator iter = features.features();
        SimpleFeature feature = iter.next(); // Only looking at first/only feature
        iter.close();

        Timestamp ts = getTimestampFromFeature(feature);

        //log.debug("getTimestampFromFeatureCollection: returning: " + ts);
        return ts;
    }

    /**
     * Attempts to extract the "timestamp" property out of the feature
     *
     * @param feature assumed to contain a "timestamp" property
     * @return An initialized Timestamp object if successful, null otherwise
     *
     * @throws GdfcException
     */
    private Timestamp getTimestampFromFeature(SimpleFeature feature) throws GdfcException {
        Timestamp ts = null;
        Object objTime = null;
        org.opengis.feature.Property propTime = null;

        if(feature != null) {
            propTime = feature.getProperty(timestampPropertyName);
        }

        if(propTime != null) {
            objTime = propTime.getValue();
            if(objTime instanceof java.lang.String) {
                //log.info("Got a String...");
                ts = getTimestampFromFeatureString((String) objTime);
            } else if(objTime instanceof java.sql.Timestamp) {
                //log.info("Got a Timestamp...");
                ts = (Timestamp) objTime;
            }
        } else {
            log.warn("No property '" + timestampPropertyName + "' found on feature! Must specify " +
                    "the name of the element in the GML containing the timestamp!");
        }

        //log.debug("getTimestampFromFeature: returning: " + ts);
        return ts;
    }


    private class GdfcException extends Exception {
        private static final long serialVersionUID = 7049430620036975247L;

        public GdfcException(String message) {
            super(message);
        }

        public GdfcException(String message, Throwable ex) {
            super(message, ex);
        }
    }

    // Property getter/setters

    public final String getDbtype() {
        return dbtype;
    }


    public final void setDbtype(final String dbtype) {
        this.dbtype = dbtype;
    }


    public final String getDbhost() {
        return dbhost;
    }


    public final void setDbhost(final String dbhost) {
        this.dbhost = dbhost;
    }


    public final int getDbport() {
        return dbport;
    }


    public final void setDbport(final int dbport) {
        this.dbport = dbport;
    }


    public final String getDbname() {
        return dbname;
    }


    public final void setDbname(final String dbname) {
        this.dbname = dbname;
    }


    public final String getDbuser() {
        return dbuser;
    }


    public final void setDbuser(final String dbuser) {
        this.dbuser = dbuser;
    }


    public final String getDbpassword() {
        return dbpassword;
    }


    public final void setDbpassword(final String dbpassword) {
        this.dbpassword = dbpassword;
    }


    public final String getCrs() {
        return crs;
    }


    public final void setCrs(final String crs) {
        this.crs = crs;
    }


    public final String getGml_version() {
        return gml_version;
    }


    public final void setGml_version(final String gml_version) {
        this.gml_version = gml_version;
    }


    public final long getDb_reset_interval() {
        return db_reset_interval;
    }


    public final void setDb_reset_interval(final long db_reset_interval) {
        this.db_reset_interval = db_reset_interval;
    }

    public String getTimestampPropertyName() {
        return timestampPropertyName;
    }

    public void setTimestampPropertyName(String timestampPropertyName) {
        this.timestampPropertyName = timestampPropertyName;
    }

    /**
     * TODO: delete once done testing/building public String getDateFormatPattern() { return dateFormatPattern; }
     * <p>
     * public void setDateFormatPattern(String dateFormatPattern) { this.dateFormatPattern = dateFormatPattern; }
     **/
    public final String getLog4jPropertyFile() {
        return log4jPropertyFile;
    }

    public final void setLog4jPropertyFile(String log4jPropertyFile) {
        this.log4jPropertyFile = log4jPropertyFile;
    }
}
