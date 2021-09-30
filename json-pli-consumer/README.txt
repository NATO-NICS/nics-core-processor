====
    Copyright (c) 2008-2016, Massachusetts Institute of Technology (MIT)
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:

    1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

    3. Neither the name of the copyright holder nor the names of its contributors
    may be used to endorse or promote products derived from this software without
    specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
    FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
    DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
    SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
    CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
    OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
    OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
====

======================
=  Sar PLI Consumer  =
======================

Initially created: 20130228

The sar-consumer app listens for UDP packet containing PLI data in JSON format, 
coverts to GML format, amd publishes the GML to the message bus for consumption
by the geodatafeed-consumer component. This will insert the data into the PostGIS
database for use by GeoServer. The SADisplay can then display the WFS generated
from this data and will show the location of the mobile devices reporting in.

A spring configuration xml file (sar-consumer.xml) contains configurable 
properties, which can be set in the sar-consumer.properties file.

BUILD

    mvn clean install

DEPLOY

To get a tar.gz file to put on a server, run:
	mvn deploy

This results in a tar file:
	target/sar-consumer-5.x-SNAPSHOT-deployable.tar.gz

Which contains a /lib directory housing all the dependencies listed in the pom,
along with the following files necessary to run the spring app:
	log4j.properties
	start.sh
	sar-consumer.properties
	sar-consumer.xml

Simply copy the gz to a server,
    tar -zxvf sar-consumer-5.x-SNAPSHOT-deployable.tar.gz
into the directory of choice, edit the above files, and add any beans to the
dependencies folder, configure a route in the xml file, and you're off.
*** Ideally you should use the component manager application to deploy this, as 
that's the structure we're using for the tar file.***
