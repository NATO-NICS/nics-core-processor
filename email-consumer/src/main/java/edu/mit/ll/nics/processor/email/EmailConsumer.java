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
package edu.mit.ll.nics.processor.email;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * Subscribes to and endpoint and publishes to another endpoint.
 */
public final class EmailConsumer {

    /**
     * <p>Member: CNAME</p>
     * <p>Description:
     * Class name for logging.
     * </p>
     */
    private static final String CNAME = EmailConsumer.class.getName();
    /**
     * <p>Member: LOG</p>
     * <p>Description:
     * The logger.
     * </p>
     */
    private static Logger LOG; // = Logger.getLogger(CNAME);
    /**
     * <p>Description:
     * The properties from the configuration file.
     * </p>
     */
    private Properties properties = null;
    /**
     * Source URL. Location of topic or room to consume
     */
    private static String srcUrl =
            "rabbitmq://localhost:5672?amqExchange=amq" +
                    ".topic&amqExchangeType=topic&requestedHeartbeat=0&routingKey=LDDRS.#.email" +
                    ".#&noAck=false&user=guest&password=guest&msgPersistent=false&msgContentType=text";
    /**
     * Mail server Url.
     */
    private static String mailUrl =
            "smtp://atc.ll.mit.edu";
    /**
     * <p>Member: kPropertiesFile</p>
     * <p>Description:
     * The URI of the properties file.
     * </p>
     */
    private static String kPropertiesFile =
            "config/email-consumer.properties";

    /**
     * Camel app that listens to a camel endpoint for XML marshalled e-mail messages then sends them out
     */
    private EmailConsumer() {
    }

    /**
     * Main.
     *
     * @param args None
     * @throws Exception Some exception.
     */
    public static void main(final String[] args) throws Exception {

        final String method = "main";
        final boolean buildRoute = true;


        configure(args); // application parameter configuration

        LOG.info("Starting Emailer");


        // Create Default/Generic Camel context
        final CamelContext context = new DefaultCamelContext();
        System.out.println("DEBUG: context := " + context.getName());

        //Create route from source to EmailSender batcher which will process 
        //incoming messages and send them out
        EmailSender batcher = new EmailSender(mailUrl);
        if(buildRoute) {

            // context requires a "final" version of an instance
            final EmailSender emailBatcher = batcher;

            context.addRoutes(new RouteBuilder() {

                @Override
                public void configure() {

                    LOG.info("Configuring Batcher");

                    // This route collects XML email messages unmarshalls them
                    //and then sends them out to the specified address
                    from(srcUrl).process(emailBatcher);
                }
            });
        }
        // Start the Context
        try {
            LOG.info("Starting Context");
            context.start();
            // sleep up to "timeOut" minutes
            while(true) {
                Thread.sleep(TimeUnit.MILLISECONDS.convert(365, TimeUnit.DAYS));
            }

        } catch(Exception e) {
            LOG.fatal("context start exception\n" + e.getMessage());
            //comp.stop();
            context.stop();
            return;
        } finally {
            LOG.info("Terminating, stopping context.");
            //comp.stop();
            context.stop();
            //return;
        }
    }

    /**
     * <p>Method: configure</p>
     * <p>Description:
     * Parses the arguments and returns a correctly configured instance.
     * </p>
     *
     * @param args The list of arguments.  First argument should be the .bpf file to load.
     * @return the instance
     */
    private static EmailConsumer configure(final String[] args) {
        final String method = "configure";

        PropertyConfigurator.configure("config/log4j.properties");
        LOG = Logger.getLogger(CNAME);
        //LOG.setLevel(Level.ALL);

        EmailConsumer cfp = null;

        // Parse arguments

        if(args.length >= 2) { // -p config [playbackfile]
            if(args[0].startsWith("-p")) {

                kPropertiesFile = args[1];
                LOG.info("Using property file "
                        + kPropertiesFile);
            }
        }
        cfp = new EmailConsumer();

        cfp.loadProperties();

        cfp.init();
        return cfp;
    }

    /**
     * <p>Method: init</p>
     * <p>Description:
     * This method will verify that the provided bpf and index file are present and that the specified properties file
     * has values identifying the MUC to stream the bpf data to.
     * </p>
     */
    private void init() {
        final String method = "init";

        String prop = null;
        String props = "Properties:";

        // do not overwrite defaulted values
        try {

            prop = properties.getProperty("srcUrl");
            if(prop != null) {
                srcUrl = prop;
            }
            props += "\nsrcUrl = " + srcUrl;

            prop = properties.getProperty("mailUrl");
            if(prop != null) {
                mailUrl = prop;
            }
            props += "\nmailUrl = " + prop;

            LOG.info("init.printprops\n" + props);
        } catch(final Exception e) {
            LOG.fatal("Error initializing properties: " + e.toString());
        }
    }

    /**
     * <p>Method: loadProperties</p>
     * <p>Description:
     * This will load the properties file identified by the kPropertiesFile string.
     * </p>
     */
    @SuppressWarnings("ALL")
    private void loadProperties() {
        final String method = "loadProperties";
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(kPropertiesFile);
            final Properties props = new Properties();
            props.load(fis);
            properties =
                    props;
            LOG.info("Loaded properties = "
                    + properties);
        } catch(final FileNotFoundException fnfe) {
            LOG.warn("Could not find properties file: " + kPropertiesFile, fnfe);
            properties =
                    new Properties();
        } catch(final IOException ioe) {
            // nearly impossible to make happen on a local file
            LOG.warn("Error in reading properities file: " + kPropertiesFile, ioe);
            properties = new Properties();
        } finally {
            if(null != fis) {
                try {
                    fis.close();
                } catch(final IOException ioe) {
                    LOG.warn("Error in closing properities file: " + kPropertiesFile,
                            ioe);
                }
            }
        }
    }
}
