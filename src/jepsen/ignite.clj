(ns jepsen.ignite
    (:require [clojure.tools.logging :refer :all]
              [jepsen
               [cli :as cli]]
              [jepsen.ignite.cas :as cas]
              [jepsen.ignite.bank :as bank]))


(def cli-opts
  "Additional command line options."
  [[nil "--name NAME" "Test name" :default "Ignite"
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
    :parse-fn read-string]])


(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run!
    (merge
      (cli/single-test-cmd
        {:test-fn  bank/test
         :opt-spec cli-opts})
      (cli/serve-cmd))
    args))