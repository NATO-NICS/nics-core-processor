<wfs:FeatureCollection  xmlns:wfs="http://www.opengis.net/wfs"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:gml="http://www.opengis.net/gml"
  xmlns:NICS="http://mapserver.nics.ll.mit.edu/NICS"
  xsi:schemaLocation="http://mapserver.nics.ll.mit.edu/NICS http://129.55.46.51:8080/geoserver/NICS/wfs?service=WFS&amp;version=1.0.0&amp;request=DescribeFeatureType&amp;typeName=NICS%3Atestgml http://www.opengis.net/wfs http://129.55.46.51:8080/geoserver/schemas/wfs/1.0.0/WFS-basic.xsd">

  <gml:featureMember>
     <NICS:testgml>
		<NICS:geom>
			<gml:Point srsName="EPSG:4326">
				<gml:coordinates>-10,10</gml:coordinates>
			</gml:Point>
		</NICS:geom>
		<NICS:name>Feature 0</NICS:name>
		<NICS:df_int>112</NICS:df_int>
		<NICS:df_str>ABCD</NICS:df_str>
    </NICS:testgml>
  </gml:featureMember>

</wfs:FeatureCollection>
