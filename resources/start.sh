#!/bin/bash
export IGNITE_HOME=/jepsen.ignite/apache-ignite-2.7.0-bin
export IGNITE_JVM_PAUSE_DETECTOR_THRESHOLD=60000
eval /jepsen.ignite/apache-ignite-2.7.0-bin/bin/ignite.sh /jepsen.ignite/server-default.xml &>>/jepsen.ignite/ignite.log &