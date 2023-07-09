#!/usr/bin/env bash
set -ex
cd "$(dirname "${BASH_SOURCE[0]}" )"/..

VERSION=$(git describe --tags --always --first-parent)
DEST=build/docker

rm -rf $DEST
mkdir -p $DEST/device-service-server/var/conf

cp ./scripts/Dockerfile $DEST/
tar -xf "./device-service-server/build/distributions/device-service-server-${VERSION}.tar" -C $DEST/device-service-server --strip-components=1
cp ./device-service-server/var/conf/conf.yml $DEST/device-service-server/var/conf

cd $DEST
docker build -t "palantirtechnologies/device-service-server:$VERSION" .
docker tag "palantirtechnologies/device-service-server:$VERSION" "palantirtechnologies/device-service-server:latest"

