FROM openjdk:17

RUN microdnf install findutils

RUN set -eux; \
    mkdir -p /opt/services;

ADD device-service-server/ /opt/services/device-service-server

RUN chmod +x /opt/services/device-service-server/bin/device-service-server

EXPOSE 8345

WORKDIR /opt/services/device-service-server/
ENTRYPOINT ["./bin/device-service-server", "server", "var/conf/conf.yml", "var/db/database.db", "var/gsmarena_data/gsmarena_dataset.csv"]
