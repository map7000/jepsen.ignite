(ns jepsen.ignite.client
    (:require [clojure.tools.logging :refer :all]
              [clojure.string :as str]
              [clojure.java.io :as io])
    (:import (clojure.lang ExceptionInfo)
             (org.apache.ignite Ignition IgniteCache)
             (org.apache.ignite.cache CacheMode CacheAtomicityMode CacheWriteSynchronizationMode)
             (org.apache.ignite.configuration CacheConfiguration)
             (org.apache.ignite.transactions TransactionConcurrency TransactionIsolation)
             (org.apache.ignite.cache.query ScanQuery)
             (org.apache.ignite.lang IgniteBiTuple)
             (java.lang Long)
             (java.io File FileNotFoundException)))

;Настройки кэша
;CacheMode	  CacheAtomicityMode		ReadFromBackup	CacheWriteSynchronizationMode
;REPLICATED	  TRANSACTIONAL		      true	          FULL_SYNC
;PARTITIONED	ATOMIC		            false	          PRIMARY_SYNC
;LOCAL	      TRANSACTIONAL_SNAPSHOT			          FULL_ASYNC

;Настройки транзакции
;TransactionConcurrency	TransactionIsolation
;PESSIMISTIC	          READ_COMMITTED
;OPTIMISTIC	            REPEATABLE_READ
;	                      SERIALIZABLE
(def cacheModes
  {"PARTITIONED" CacheMode/PARTITIONED
   "REPLICATED"  CacheMode/REPLICATED
   "LOCAL"       CacheMode/LOCAL})
(def cacheAtomicityModes
  {"TRANSACTIONAL"          CacheAtomicityMode/TRANSACTIONAL
   "ATOMIC"                 CacheAtomicityMode/ATOMIC
   "TRANSACTIONAL_SNAPSHOT" CacheAtomicityMode/TRANSACTIONAL_SNAPSHOT})
(def readFromBackups
  {"true"  true
   "false" false})
(def cacheWriteSynchronizationModes
  {"FULL_SYNC"    CacheWriteSynchronizationMode/FULL_SYNC
   "PRIMARY_SYNC" CacheWriteSynchronizationMode/PRIMARY_SYNC
   "FULL_ASYNC"   CacheWriteSynchronizationMode/FULL_ASYNC})

(def transactionConcurrencys
  {"OPTIMISTIC"  TransactionConcurrency/OPTIMISTIC
   "PESSIMISTIC" TransactionConcurrency/PESSIMISTIC})
(def transactionIsolations
  {"SERIALIZABLE"    TransactionIsolation/SERIALIZABLE
   "READ_COMMITTED"  TransactionIsolation/READ_COMMITTED
   "REPEATABLE_READ" TransactionIsolation/REPEATABLE_READ})

(defn createCache!
  [ignite
   cacheName
   cacheMode
   cacheAtomicityMode
   cacheWriteSynchronizationMode
   readFromBackup]
  (let [cfg (new CacheConfiguration cacheName)]
    (do
      (.setTypes cfg (.getClass Long) (.getClass Long))
      (.setBackups cfg 10)
      (.setCacheMode cfg cacheMode)
      (.setAtomicityMode cfg cacheAtomicityMode)
      (.setWriteSynchronizationMode cfg cacheWriteSynchronizationMode)
      (.setReadFromBackup cfg readFromBackup)
      (.setIndexedTypes cfg (into-array (list (.getClass Long) (.getClass Long))))
      (.getOrCreateCache ignite cfg))))

(defn destroyCache!
  [ignite cacheName]
  (.destroyCache ignite cacheName))
(defn putValue!
  [ignite cacheName key value]
  (let [cache (.cache ignite cacheName)] (.put cache (long key) (long value))))
(defn getValue
  [ignite cacheName key]
  (let [cache (.cache ignite cacheName)] (.get cache (long key))))
(defn getValue
  [cache key]
  (.get cache (long key)))
(defn updateBalance!
  [ignite cacheName key change]
  (let [cache (.cache ignite cacheName)
        value (.get cache (long key))]
    (.put cache (long key) (long (+ value change)))))
(defn getCacheValues
  [ignite cacheName]
  (let [cache (.cache ignite cacheName)]
    (map #(^Long .getValue %) (iterator-seq (.iterator (.query cache (new ScanQuery nil)))))))
(defn getCacheKeys
  [ignite cacheName]
  (let [cache (.cache ignite cacheName)]
    (map #(^Long .getKey %) (iterator-seq (.iterator (.query cache (new ScanQuery nil)))))))

(defn startClient!
  []
  (Ignition/start "./resources/client-default.xml"))