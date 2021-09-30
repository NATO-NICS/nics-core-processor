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

import edu.mit.ll.nics.processor.incorg.IncOrgProcessor;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;


/**
 * Tests the {@link IncOrgProcessor}
 */
public class IncOrgProcessorTest {

    private static IncOrgProcessor processor = new IncOrgProcessor();

    @BeforeClass
    public void before() {
        // TODO: set all these via testng config xml
        processor.setIdentityHeader("x-remote-user");
        processor.setEmapi("http://localhost:8080/em-api/v1");
        processor.setIdentityOrgId(1);
        processor.setIdentityUser("test.user@localhost.local");
        processor.setRoomsConfig("{\"rooms\":[{\"roomName\":\"Working Map\", \"isSecure\":false}, " +
                "{\"roomName\":\"Command\", \"isSecure\":true}], \"template\":\"%s (%s)\"}");
    }

    @Test(testName = "TestPopulateOrgs")
    public void testPopulateOrgs() {
        processor.populateOrgs();

        // TODO: verify orgs were fetched? Not really a unit test, more of an integration, since it relies on the method
        //  going out to a configured API to get data...

    }

}
