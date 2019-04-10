(ns jepsen.ignite
    (:require [clojure.tools.logging :refer :all]
              [clojure.string :as str]
              [jepsen
               [cli :as cli]
               ;               [tests :as tests]
               [control :as c]
               [db :as db]
               [util :as ju]
               [client :as client]
               [checker :as checker]
               [nemesis :as nemesis]
               [generator :as gen]
               [checker :as checker]
               [checker :as checker]
               [independent :as independent]]
              [jepsen.checker.timeline :as timeline]
              [knossos.model :as model]
              [jepsen.control.util :as cu]
              [jepsen.ignite.support :as s]
              [jepsen.ignite.cas :as cas]
              [jepsen.ignite.bank :as bank])
    (:import (clojure.lang ExceptionInfo)
             (org.apache.ignite Ignition IgniteCache)
             (org.apache.ignite.cache CacheMode CacheAtomicityMode)
             (org.apache.ignite.configuration CacheConfiguration)
             (org.apache.ignite.transactions TransactionConcurrency TransactionIsolation)
             (java.io File FileNotFoundException)))

(def testNames
  {"cas"  cas/test
   "bank" bank/test})

(def cli-opts
  "Additional command line options."
  [[nil
    "--name NAME"
    "Test name"
    :default  "Ignite"
    :parse-fn read-string]
   [nil
    "--cacheName CacheName"
    "CacheName"
    :default  "Jepsen"
    :parse-fn read-string]
   [nil
    "--cacheMode CacheMode"
    "CacheMode"
    :default  "REPLICATED"
    :parse-fn read-string]
   [nil
    "--cacheAtomicityMode CacheAtomicityMode"
    "CacheAtomicityMode"
    :default  "TRANSACTIONAL"
    :parse-fn read-string]
   [nil
    "--readFromBackup ReadFromBackup"
    "ReadFromBackup"
    :default  "false"
    :parse-fn read-string]
   [nil
    "--cacheWriteSynchronizationMode CacheWriteSynchronizationMode"
    "CacheWriteSynchronizationMode"
    :default  "FULL_SYNC"
    :parse-fn read-string]
   [nil
    "--transactionConcurrency TransactionConcurrency"
    "TransactionConcurrency"
    :default  "PESSIMISTIC"
    :parse-fn read-string]
   [nil
    "--transactionIsolation TransactionIsolation"
    "TransactionIsolation"
    :default  "REPEATABLE_READ"
    :parse-fn read-string]
   [nil
    "--test TestName"
    "TestName"
    :default  "cas"
    :parse-fn read-string]])


(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (let [testName (get testNames (:test args))]
    (info testName)
    (cli/run!
     (merge
      (cli/single-test-cmd
       {:test-fn  bank/test
        :opt-spec cli-opts})
      (cli/serve-cmd))
     args)))