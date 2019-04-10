(ns jepsen.ignite.cas
    (:require [clojure.tools.logging :refer :all]
              [clojure.string :as str]
              [jepsen
               [cli :as cli]
               [tests :as tests]
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
              [jepsen.ignite.client :as c])
    (:import (clojure.lang ExceptionInfo)
             (java.lang System)
             (org.apache.ignite Ignition IgniteCache)
             (org.apache.ignite.cache CacheMode CacheAtomicityMode CacheWriteSynchronizationMode)
             (org.apache.ignite.configuration CacheConfiguration)
             (org.apache.ignite.transactions TransactionConcurrency TransactionIsolation)
             (java.io File FileNotFoundException)))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})

(defn ignite-cas! [cache key value new-value]
  (let [existing-value (.get cache key)]
    (if (= existing-value value)
      (let [r (.put cache key new-value)]
        true)
      false)))

(defn ignite-transaction-cas! [ignite cache key value new-value transactionConcurrency transactionIsolation]
  (let [transaction (.transactions ignite)]
    (with-open [tx (.txStart transaction transactionConcurrency transactionIsolation)]
      (let [existing-value (.get cache key)]
        (if (= existing-value value)
          (let [r (.put cache key new-value)]
            (.commit tx)
            true)
          (let []
            (.commit tx)
            false))))))

(defrecord CasRegisterClient
  [ignite
   cacheName
   cacheMode
   cacheAtomicityMode
   readFromBackup
   cacheWriteSynchronizationMode
   transactionConcurrency
   transactionIsolation]
  client/Client
  (open! [this test node]
    (System/setProperty "IGNITE_JVM_PAUSE_DETECTOR_THRESHOLD" "60000")
    (System/setProperty "IGNITE_UPDATE_NOTIFIER" "false")
    (assoc this :ignite (c/startClient!)))
  (setup! [this test]
    (c/createCache! ignite cacheName cacheMode cacheAtomicityMode cacheWriteSynchronizationMode readFromBackup))
  (invoke! [this test op]
    (let [[key v] (:value op)]
      (case (:f op)
            :read  (let [value (c/getValue ignite cacheName key)]
                     (assoc op :type :ok :value (independent/tuple key value)))
            :write (do (c/putValue! ignite cacheName key v) (assoc op :type :ok))
            :cas   (let [[value value'] v
                         cache          (.cache ignite cacheName)
                         ok?            (ignite-transaction-cas! ignite cache key value value' transactionConcurrency transactionIsolation)]
                     (assoc op :type (if ok? :ok :fail))))))
  (teardown! [this test] (.close ignite))
  (close! [this test] (.close ignite)))


(defn test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (let [cacheMode                     (get c/cacheModes (:cacheMode opts))
        cacheAtomicityMode            (get c/cacheAtomicityModes (:cacheAtomicityMode opts))
        readFromBackup                (get c/readFromBackups (:readFromBackup opts))
        cacheWriteSynchronizationMode (get c/cacheWriteSynchronizationModes (:cacheWriteSynchronizationMode opts))
        transactionConcurrency        (get c/transactionConcurrencys (:transactionConcurrency opts))
        transactionIsolation          (get c/transactionIsolations (:transactionIsolation opts))]
    (System/setProperty "IGNITE_JVM_PAUSE_DETECTOR_THRESHOLD" "60000")
    (merge tests/noop-test
           opts
           {:name      (str (:name opts) "_BANK_" (:cacheMode opts) "_" (:cacheAtomicityMode opts) "_" (:readFromBackup opts) "_" (:cacheWriteSynchronizationMode opts) "_" (:transactionConcurrency opts) "_" (:transactionIsolation opts))
            :db        (s/db "2.7.0")
            :client    (CasRegisterClient. nil (:cacheName opts) cacheMode cacheAtomicityMode readFromBackup cacheWriteSynchronizationMode transactionConcurrency transactionIsolation)
            :generator (->>
                        (independent/concurrent-generator 10 (range)
                                                          (fn [k]
                                                            (->> (gen/mix [r w cas])
                                                                 (gen/stagger 1/50)
                                                                 (gen/limit 1000))))
                        (gen/nemesis
                         (gen/seq
                           (cycle
                             [(gen/sleep 60)
                              {:type :info, :f :start}
                              (gen/sleep 60)
                              {:type :info, :f :stop}])))
                        (gen/time-limit (:time-limit opts)))
            :model     (model/cas-register)
            ;          :nemesis (s/killer)

            :nemesis   (nemesis/partition-random-node)
            :checker   (checker/compose
                        {:perf  (checker/perf)
                         :indep (independent/checker
                                 (checker/compose
                                  {:timeline (timeline/html)
                                   :linear   (checker/linearizable)}))})})))