#!/bin/bash
export IGNITE_HOME=/tmp/jepsen.ignite/apache-ignite-2.7.0-bin/bin
export IGNITE_JVM_PAUSE_DETECTOR_THRESHOLD=60000
eval /tmp/jepsen.ignite/apache-ignite-2.7.0-bin/bin/ignite.sh /tmp/jepsen.ignite/server-default.xml &>>/tmp/jepsen.ignite/ignite.log &