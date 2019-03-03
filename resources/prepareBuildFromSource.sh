#!/bin/bash
if [ ! -d ignite ]; then
  eval git clone https://github.com/apache/ignite.git
fi
cd ignite
eval git pull
eval mvn -q clean package -DskipTests -Prelease,lgpl
eval rm -rf target/release-package-apache-ignite/libs/optional
cd target
mv release-package-apache-ignite ignite
eval tar -zcf ignite.tar.gz ignite
mv ignite.tar.gz ../..
