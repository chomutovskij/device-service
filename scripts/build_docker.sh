#!/usr/bin/env bash
set -ex
cd "$(dirname "${BASH_SOURCE[0]}" )"/..

VERSION=$(git describe --tags --always --first-parent)
DEST=build/docker

rm -rf $DEST
mkdir -p $DEST/device-service-server/var/conf
mkdir -p $DEST/device-service-server/var/db
mkdir -p $DEST/device-service-server/var/gsmarena_data
mkdir -p $DEST/device-service-server/var/certs

cp ./scripts/Dockerfile $DEST/
tar -xf "./device-service-server/build/distributions/device-service-server-${VERSION}.tar" -C $DEST/device-service-server --strip-components=1
cp ./device-service-server/var/conf/conf.yml $DEST/device-service-server/var/conf
cp ./device-service-server/var/db/database.db $DEST/device-service-server/var/db
cp ./device-service-server/var/gsmarena_data/gsmarena_dataset.csv $DEST/device-service-server/var/gsmarena_data
cp ./device-service-server/var/certs/keystore.jks $DEST/device-service-server/var/certs
cp ./device-service-server/var/certs/truststore.jks $DEST/device-service-server/var/certs

cd $DEST
docker build -t "chomutovskij/device-service-server:$VERSION" .
docker tag "chomutovskij/device-service-server:$VERSION" "chomutovskij/device-service-server:latest"
