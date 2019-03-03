#!/bin/bash
export IGNITE_HOME=/jepsen.ignite/ignite/bin
export IGNITE_JVM_PAUSE_DETECTOR_THRESHOLD=60000
eval /jepsen.ignite/ignite/bin/ignite.sh /jepsen.ignite/server-default.xml &>>/jepsen.ignite/ignite.log &