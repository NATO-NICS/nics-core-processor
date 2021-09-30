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

COMPONENT
geodatafeed-consumer

DESCRIPTION
Consumes GML PLI messages, and inserts them into a PostGIS database, which
correspond to WFS layers that can be placed in the SADISPLAY.

FILES

src/main/config/spring/geodatafeed-consumer.xml
-------------------------------------------------
Main Spring configuration. This shouldn't normally need to be modified unless
the routes are changing. Everything that is/should be configurable can be set
in the properties file.

src/main/config/geodatafeed-consumer.properties
------------------------------------------------
The properties file for configuring geodatafeed-consumer for different nodes and
environments. Individual configuration for different VMs may be present with a 
.### postfix of the VM IP.
*** The Component Manager application will manage properties on a node-by-node
basis. This properties file just serves a skeleton properties file or set of
defaults. ***


src/main/scripts/start.sh
--------------------------
The start script to run geodatafeed-consumer.

BUILD/DEPLOY
mvn (clean compile) package builds deployable tar named
	geodatafeed-consumer-<VERSION>.tar.gz
This tar contains the files needed to run it, including dependencies, and is
meant to be used with the Component Manager application for deployment.
