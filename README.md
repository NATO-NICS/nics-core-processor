## Synopsis

The Next-Generation Incident Command System (NICS) processor services (data feed consumers, etc.)

## Dependencies
- nics-assembly
- nics-common

## Building

    mvn package


## Description

 - collabfeed-manager - Listens for new feature messages on the iweb.NICS.# topic, creates a datalayer on geoserver and maintains the status of that layer based on user changes
 - component-manager-archive-builder - Used by individual modules to package up the component into a deployable tar
 - geodatafeed-consumer - Consumes AVL/PLI in the form of GML, and persists to a datafeed database which populates layers in GeoServer
 - gst2gml -
 - json-pli-consumer - Consumes AVL/PLI in the standard NICS JSON PLI format, and publishes GML to geodatafeed-consumer
 - spring-runner - Barebones example component to use as a template for writing your own consumer


## Documentation

Further documentation is available at nics-common/docs
