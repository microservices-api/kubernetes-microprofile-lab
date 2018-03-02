#FROM websphere-liberty:microProfile
FROM arthurdm/websphere-liberty
#RUN installUtility install  --acceptLicense logstashCollector-1.0
COPY server.xml /config/server.xml
RUN installUtility install --acceptLicense defaultServer
COPY target/microservice-vote-1.0.0-SNAPSHOT.war /config/apps/vote.war
