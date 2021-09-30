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
package edu.mit.ll.nics.processor.test.incorg;

import org.apache.camel.Endpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


/**
 * Not so much a test, as a way to send payloads to the routes below for debugging purposes.
 */
public class IncOrgCamelTest extends CamelSpringTestSupport {

    // TODO: get a test properties reader for filling in these test URIs and other settings

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("test-incorg-room-creator.xml");
    }

    /*@Test
    public void testPayloadIsReached() throws InterruptedException {
        MockEndpoint mockOut = getMockEndpoint("mock:out");
        mockOut.setExpectedMessageCount(1);
        template.sendBody("direct:in", "this is test");
        assertMockEndpointsSatisfied();
    }*/

    @Test
    public void testEscalateIsReached() throws InterruptedException {
        // TODO: see about getting endpoint, that we can tie into, and do assertions on?
        Endpoint endpoint = context.getRoute("incidentEscalation").getEndpoint();
        String uri = endpoint.getEndpointUri();

        template.sendBody(uri,
                "{\"incidentid\":1, \"usersessionid\":25, \"incidentname\":\"Some Name\", " +
                "\"workspaceid\":1,\"incidentIncidenttypes\":[{\"incidenttypeid\":17, \"incidentid\":1}]}");

        // Runs it, but not testing anything?

    }

    @Test
    public void testIncidentCreated() throws InterruptedException {
        Endpoint endpoint = context.getRoute("incidentCreated").getEndpoint();
        String uri = endpoint.getEndpointUri();

        template.sendBody(uri,
                "{\"incidentid\":1, \"usersessionid\":25, \"incidentname\":\"Some Name\", " +
                        "\"workspaceid\":1,\"incidentIncidenttypes\":[{\"incidenttypeid\":17, \"incidentid\":1}]}");

    }

    @Test
    public void testIncidentUpdated() throws InterruptedException {
        Endpoint endpoint = context.getRoute("incidentUpdated").getEndpoint();
        String uri = endpoint.getEndpointUri();

        template.sendBody(uri,
                "{\"incidentid\":1, \"usersessionid\":25, \"incidentname\":\"Some Name\", " +
                        "\"workspaceid\":1,\"incidentIncidenttypes\":[{\"incidenttypeid\":17, \"incidentid\":1}]}");

    }

    @Test
    public void testOrgAdded() throws InterruptedException {
        Endpoint endpoint = context.getRoute("orgAdded").getEndpoint();
        String uri = endpoint.getEndpointUri();

        template.sendBody(uri,
                "{\"incidentid\":1, \"usersessionid\":25, \"incidentname\":\"Some Name\", " +
                        "\"workspaceid\":1,\"incidentIncidenttypes\":[{\"incidenttypeid\":17, \"incidentid\":1}]}");

    }

}
