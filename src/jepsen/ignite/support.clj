(ns jepsen.ignite.support
    (:require [clojure.tools.logging :refer :all]
              [clojure.string :as str]
              [clojure.java.io :as io]
              [jepsen
               [cli :as cli]
               [tests :as tests]
               [control :as c]
               [core :as jepsen]
               [db :as db]
               [util :as ju]
               [client :as client]
               [checker :as checker]
               [nemesis :as nemesis]
               [generator :as gen]
               [independent :as independent]]
              [jepsen.checker.timeline :as timeline]
              [knossos.model :as model]
              [jepsen.control.util :as cu])
    (:import (clojure.lang ExceptionInfo)
             (org.apache.ignite Ignition IgniteCache)
             (org.apache.ignite.cache CacheMode CacheAtomicityMode CacheWriteSynchronizationMode)
             (org.apache.ignite.configuration CacheConfiguration)
             (org.apache.ignite.transactions TransactionConcurrency TransactionIsolation)
             (java.lang Integer)
             (java.io File FileNotFoundException)))

(def dir "/jepsen.ignite")
(def logfile (str dir "/ignite.log"))
(def pidfile (str dir "/ignite.pid"))

(defn localNodeLogFileName
  [node]
  (str/replace (str "/tmp/node" node ".log") #":" "_"))

(defn killStalledNodes
  "Kill stalled Apache Ignite."
  [node test]
  (info node "killing stalled nodes")
  (ju/meh
   (c/exec :pkill :-9 :-f "org.apache.ignite.startup.cmdline.CommandLineStartup")))

(defn awaitStartedGrid
  "Waiting for started grid"
  [node test]
  (info node "waiting for topology snapshot")
  (io/delete-file (localNodeLogFileName node) true)
  (c/download (str dir "/ignite.log") (localNodeLogFileName node))
  (while
   (not (str/includes? (slurp (localNodeLogFileName node)) "servers=5"))
   (Thread/sleep 5000)
   (c/download (str dir "/ignite.log") (localNodeLogFileName node))))


(defn install!
  "Copy Ignite to nodes."
  [node version]
  (ju/meh (c/exec :rm :-rf dir))
  (ju/meh (c/exec :mkdir dir))
  (c/exec :touch (str dir "/ignite.log"))
  (c/cd (str dir)
        (info node (str "Copy Ignite-" version))
        (c/upload (str "./resources/ignite.tar.gz") dir)
        (info node (str "Untar Ignite-" version))
        (c/exec :tar :-zxf :ignite.tar.gz)))

(defn configure!
  "Uploads configuration files to the given node."
  [node]
  (info node "Configuring Ignite grid")
  (c/upload "./resources/server-default.xml" dir)
  (c/upload "./resources/start.sh" dir))

(defn start!
  "Starts Apache Ignite."
  [node]
  (info node (str "Start Ignite "))
  (c/exec :/bin/bash (str dir "/start.sh")))

(defn stop!
  "Stops Apache Ignite."
  [node]
  (info node "Killing Ignite")
  (ju/meh
   (c/exec
    (c/lit "ps -ef | grep ignite.sh | grep -v grep | awk '{print $2}'| xargs kill -9"))))

(defn clean!
  "Wipes data."
  [node]
  (info node "Deleting data files")
  (c/exec (c/lit (str "rm -rf " dir "/*"))))


(defn db
  "Ignite for a particular version."
  [version]
  (reify
   db/DB
   (setup! [_ test node]
           (killStalledNodes node test)
           (install! node version)
           (configure! node)
           (jepsen/synchronize test)
           (start! node)
           (awaitStartedGrid node test)
           (jepsen/synchronize test))
   (teardown! [_ test node]
              (stop! node)
              (clean! node))

   db/LogFiles
   (log-files [_ test node]
              [logfile])))

(defn killer
  "Kills random node"
  []
  (nemesis/node-start-stopper
   rand-nth
   (fn start [test node] (stop! node))
   (fn stop [test node] (start! node))))