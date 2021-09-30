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

import edu.mit.ll.nics.common.email.EmailType;
import edu.mit.ll.nics.common.email.JsonEmail;
import edu.mit.ll.nics.common.email.XmlEmail;
import edu.mit.ll.nics.common.email.exception.JsonEmailException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.activation.DataHandler;
import javax.imageio.ImageIO;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.config.TransportStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Processes XML message to extract e-mail message and sends message using specified mail server
 */
public class EmailConsumerSpring implements Processor {

    /**
     * <p>Member: LOG</p>
     * <p>Description:
     * The logger.
     * </p>
     */
    private static final Logger LOG = LoggerFactory.getLogger(EmailConsumerSpring.class);

    private Unmarshaller unmarsh = null;
    private EmailType email = null;

    // Properties

    /**
     * The log4j.properties file used by the Spring application
     */
    private String log4jPropertyFile;

    private String smtpHost;

    private String smtpPort;

    private String smtpStartTLS;

    private String smtpSSL;

    private String smtpAuth;

    private String smtpUsername;

    private String smtpPassword;

    /**
     * Default constructor, required by Spring
     */
    public EmailConsumerSpring() {
    }

    /**
     * This method is called by Spring once all the properties have been read as specified in the spring .xml file
     *
     * @throws JAXBException
     */
    public void init() throws JAXBException {
        PropertyConfigurator.configure(log4jPropertyFile);


        try { // create JAXB objects
            JAXBContext jaxbContext = JAXBContext.newInstance(XmlEmail.class.getPackage().getName());
            unmarsh = jaxbContext.createUnmarshaller(); // get an unmarshaller
        } catch(JAXBException e) {
            LOG.warn("Exception getting JAXB unmarshaller: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Unmarshall xml message into an EmailType JAXB element then send the e-mail
     *
     * @param e
     */
    @Override
    public void process(Exchange e) {
        // get the XML message from the exchange
        String body = e.getIn().getBody(String.class);
        LOG.debug("Processing Message: " + body);

        if(isSimpleEmailMessage(body)) {
            handleSimpleEmailMessage(body);
        } else {
            handleXmlEmailMessage(body);
        }

    }

    private boolean isSimpleEmailMessage(final String body) {
        try {
            JSONObject json = new JSONObject(body);
            LOG.debug("Message is JSON");
            return true;
        } catch(JSONException je) {
            LOG.debug("Message not JSON");
        }

        return false;
    }

    private boolean testJsonArray(String val) {
        try {
            if(val.startsWith("[")) {
                JSONArray arr = new JSONArray(val);
            }

            return true;
        } catch(JSONException je) {
            return false;
        }
    }

    private String getRecipientsFromJson(String val) {
        StringBuffer ret = new StringBuffer();
        try {
            if(val.startsWith("[")) {
                JSONArray arr = new JSONArray(val);
                LOG.debug("Emails in array: " + arr.length());
                for(int i = 0; i < arr.length(); i++) {
                    String email = arr.getString(i);
                    LOG.debug("Extracting email from JSONArray: " + email);

                    if(ret.length() != 0) {
                        ret.append(",");
                    }
                    ret.append(email);
                }
            }
        } catch(JSONException je) {

        }

        return ret.toString();
    }

    private String validateRecipients(String recipients) {
        if(testJsonArray(recipients)) {
            recipients = getRecipientsFromJson(recipients);
        }
        Pattern pattern = Pattern.compile(
                "^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");
        LOG.debug("Validating receipients: " + recipients);
        StringBuffer validated = new StringBuffer();
        String[] addresses = recipients.split(",");
        for(int i = 0; i < addresses.length; i++) {
            String email = addresses[i].trim();
            if(pattern.matcher(email).find()) {
                if(validated.length() != 0) {
                    validated.append(",");
                }
                validated.append(email);
            } else {
                LOG.debug("Removing invalid address: " + addresses[i]);
            }
        }
        return validated.toString();
    }

    private Session createSession(String from) {
        Properties props = new Properties();
        Session session = null;
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.from", from);
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        if(Boolean.parseBoolean(smtpStartTLS)) {
            props.put("mail.smtp.starttls.enable", true);
        }
        if(Boolean.parseBoolean(smtpAuth)) {
            Authenticator auth = new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            };
            session = Session.getDefaultInstance(props, auth);
        } else {
            session = Session.getDefaultInstance(props, null);
        }

        return session;
    }

    private MimeMessage createMimeMessage(Session session) {
        return new MimeMessage(session);
    }

    private MimeMessage createMimeMessage(Session session, String to, String subject)
            throws MessagingException {
        MimeMessage msg = createMimeMessage(session);

        msg.setRecipients(Message.RecipientType.TO,
                validateRecipients(to));

        msg.setSubject(subject);

        return msg;
    }

    private void handleSimpleEmailMessage(String message) {
        try {
            JsonEmail je = JsonEmail.fromJSONString(message);
            final String to = je.getTo().trim();
            final String from = je.getFrom().trim();
            final String subject = je.getSubject().trim();
            final String body = je.getBody();

            Email email = EmailBuilder.startingBlank()
                    .from(from)
                    .to(to)
                    .withSubject(subject)
                    .withPlainText(body)
                    .buildEmail();
            MailerBuilder.MailerRegularBuilder mBuild = MailerBuilder
                    .withDebugLogging(false);
            if(LOG.isDebugEnabled()) {
                mBuild = mBuild.withDebugLogging(true);
            }
            if(Boolean.parseBoolean(smtpAuth)) {
                mBuild = mBuild.withSMTPServer(smtpHost, Integer.parseInt(smtpPort), smtpUsername, smtpPassword);
            } else {
                mBuild = mBuild.withSMTPServer(smtpHost, Integer.parseInt(smtpPort));
            }
            if(Boolean.parseBoolean(smtpStartTLS)) {
                mBuild = mBuild.withTransportStrategy(TransportStrategy.SMTP_TLS);
            } else if(Boolean.parseBoolean(smtpSSL)) {
                mBuild = mBuild.withTransportStrategy(TransportStrategy.SMTPS);
            }
            Mailer mailer = mBuild.buildMailer();
            mailer.sendMail(email);
            LOG.debug("Message sent");
        } catch(JsonEmailException jee) {
            LOG.error("Caught JsonEmailException");
            jee.printStackTrace();
        } catch(Exception ex) {
            LOG.error("Caught Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void handleXmlEmailMessage(String body) {
        // put the body into a string reader class
        java.io.StringReader sr = new java.io.StringReader(body);

        JAXBElement<EmailType> email_t;
        try {
            //Unmarshall the XML into Email object
            email_t = (JAXBElement<EmailType>) unmarsh.unmarshal(sr);
            email = email_t.getValue();

            //Build MimeMessage from email object
            Session session = createSession(email.getHeader().getFrom());
            try {
                //Add e-mail header

                MimeMessage msg = createMimeMessage(session, email.getHeader().getTo(), email.getHeader().getSubject());

                // add CC recipients
                if(email.getHeader().getCc() != null) {
                    msg.addRecipients(Message.RecipientType.CC,
                            validateRecipients(email.getHeader().getCc()));
                }

                //Create and add the e-mail body
                //If no images are included just add body text
                if(email.getContent().getImage().getLocation() == null
                        && email.getContent().getBody().getFormat() != null) {
                    String body_text = email.getContent().getBody().getText();
                    if(email.getContent().getBody().getFormat().equals("HTML")) {
                        msg.setContent(body_text, "text/html");
                    } else {
                        msg.setText(body_text);
                    }
                } else {
                    //If Images are included create a multipart email body
                    Multipart multipartbody;
                    //Buffer the image
                    BufferedImage img = (BufferedImage) email.getContent().getImage().getJPEGPicture();
                    //Create a message body part and add the image
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpeg", bos);
                    MimeBodyPart imageBodyPart = new MimeBodyPart();
                    imageBodyPart
                            .setDataHandler(new DataHandler(new ByteArrayDataSource(bos.toByteArray(), "image/jpeg")));
                    //Create the multipart body and add the image bodypart
                    if(email.getContent().getImage().getLocation().equals("embed")) {
                        //Embed image
                        multipartbody = new MimeMultipart("related");
                        imageBodyPart.setHeader("Content-ID", "<embedded_image>");
                    } else {
                        //Attach image
                        multipartbody = new MimeMultipart();
                        imageBodyPart.setFileName("image.jpg");
                    }

                    //add the text bodypart
                    if(email.getContent().getBody().getFormat() != null) {
                        MimeBodyPart messageBodyPart = new MimeBodyPart();
                        String body_text = email.getContent().getBody().getText();
                        if(email.getContent().getImage().getLocation().equals("embed")
                                && email.getContent().getBody().getFormat().equals("HTML")) {
                            //Embedded image in html message
                            //Insert image at end of body tag
                            messageBodyPart.setContent(body_text.substring(0, body_text.lastIndexOf("</body>"))
                                    + ("<br/><br/><img src=\"cid:embedded_image\">")
                                    + (body_text.substring(body_text.lastIndexOf("</body>"))), "text/html");
                        } else if(email.getContent().getImage().getLocation().equals("embed")) {
                            //Embedded image in regular text body
                            //Convert into html message
                            messageBodyPart.setContent("<html><body>"
                                    + body_text + "<br/><br/><img src=\"cid:embedded_image\">"
                                    + "</body></html>", "text/html");
                        } else if(email.getContent().getBody().getFormat().equals("HTML")) {
                            //Attached image with html body
                            messageBodyPart.setContent(body_text, "text/html");
                        } else {
                            //Attached image with text body
                            messageBodyPart.setText(body_text);
                        }
                        multipartbody.addBodyPart(messageBodyPart);
                        multipartbody.addBodyPart(imageBodyPart);
                    }
                    msg.setContent(multipartbody);
                }

                //Send the message
                Transport.send(msg);
                LOG.info("Message sent to:" + email.getHeader().getTo());
            } catch(MessagingException mex) {
                System.out.println("send failed, exception: " + mex);
            }
        } catch(Exception ex) {
            LOG.error("EmailSender:process: caught following "
                    + "exception processing XML: "
                    + ex.getMessage(), ex);
        }
    }


    // Getters and Setters

    public String getLog4jPropertyFile() {
        return log4jPropertyFile;
    }

    public void setLog4jPropertyFile(String log4jPropertyFile) {
        this.log4jPropertyFile = log4jPropertyFile;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public String getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(String smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public String getSmtpStartTLS() {
        return smtpStartTLS;
    }

    public void setSmtpStartTLS(String smtpStartTLS) {
        this.smtpStartTLS = smtpStartTLS;
    }

    public String getSmtpSSL() {
        return smtpSSL;
    }

    public void setSmtpSSL(String smtpSSL) {
        this.smtpSSL = smtpSSL;
    }

    public String getSmtpAuth() {
        return smtpAuth;
    }

    public void setSmtpAuth(String smtpAuth) {
        this.smtpAuth = smtpAuth;
    }
}


