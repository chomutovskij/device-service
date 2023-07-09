FROM openjdk:15

RUN set -eux; \
    mkdir -p /opt/palantir/services;

ADD device-service-server/ /opt/palantir/services/device-service-server

RUN chmod +x /opt/palantir/services/device-service-server/bin/device-service-server

EXPOSE 8345

WORKDIR /opt/palantir/services/device-service-server/
ENTRYPOINT ["./bin/device-service-server", "server", "var/conf/conf.yml"]
