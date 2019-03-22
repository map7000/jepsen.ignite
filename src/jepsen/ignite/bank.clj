(ns jepsen.ignite.bank
    "Simulates transfers between bank accounts"
    (:refer-clojure :exclude
                    [test])
    (:require [jepsen
               [client :as client]
               [tests :as tests]
               [checker :as checker]
               [generator :as gen]
               [util :as util
                :refer   [meh]]]
              [jepsen.checker.timeline :as timeline]
              [jepsen.ignite.client :as c]
              [clojure.core.reducers :as r]
              [jepsen.ignite.support :as s]
              [clojure.tools.logging :refer :all]
              [knossos.op :as op]))

(def cacheName "accounts")

(defrecord BankClient
  [ignite
   cacheName
   cacheMode
   cacheAtomicityMode
   readFromBackup
   cacheWriteSynchronizationMode
   transactionConcurrency
   transactionIsolation
   tbl-created?
   n
   starting-balance]
  client/Client
  (open! [this test node]
    (System/setProperty "IGNITE_JVM_PAUSE_DETECTOR_THRESHOLD" "60000")
    (System/setProperty "IGNITE_UPDATE_NOTIFIER" "false")
    (assoc this :ignite (c/startClient!)))
  (setup! [this test]
    (info "Start setup")
    (locking tbl-created?
             (when (compare-and-set! tbl-created? false true)
                   (info "Setup")
                   (c/destroyCache! ignite cacheName)
                   (info "Creating table")
                   (c/createCache! ignite cacheName cacheMode cacheAtomicityMode cacheWriteSynchronizationMode readFromBackup)
                   (dotimes [i n]
                     (info "Creating account" i starting-balance)
                     (c/putValue! ignite cacheName i starting-balance)))))

  (invoke! [this test op]
    (case (:f op)
          :read (let [transaction (.transactions ignite)
                      cache       (.cache ignite cacheName)]
                  (let [tx (.txStart transaction transactionConcurrency transactionIsolation)]
                    (.timeout tx 2000)
                    (try
                      (let [value (vals (.getAll cache (set (range n))))]
                        (.commit tx)
                        (assoc op :type :ok :value value))
                      (catch org.apache.ignite.transactions.TransactionTimeoutException eTimeOut (info "TransactionTimeoutException") (assoc op :type :fail, :error [:timeout]))
                      (catch org.apache.ignite.transactions.TransactionDeadlockException eDeadLock (info "TransactionDeadlockException") (assoc op :type :fail, :error [:deadlock]))
                      (catch org.apache.ignite.transactions.TransactionOptimisticException eOptimistic (info "TransactionOptimisticException")
                        (assoc op :type :fail, :error [:optimistic]))
                      (catch javax.cache.CacheException e (info "exception") (assoc op :type :fail, :error [:javax.cache.CacheException]))
                      (catch java.lang.Exception e2 (info "exception2") (assoc op :type :fail, :error [:java.lang.Exception]))
                      (finally (.close tx)))))
          :transfer
          (let [transaction (.transactions ignite)
                cache       (.cache ignite cacheName)]
            (let [tx (.txStart transaction transactionConcurrency transactionIsolation)]
              (.timeout tx 2000)
              (try
                (let [{:keys [from to amount]} (:value op)
                      b1                       (- (c/getValue cache from) amount)
                      b2                       (+ (c/getValue cache to) amount)]
                  (cond
                   (neg? b1)
                   (do (.commit tx) (assoc op :type :fail, :error [:negative from b1]))
                   (neg? b2)
                   (do (.commit tx) (assoc op :type :fail, :error [:negative to b2]))
                   true
                   (do
                     (c/putValue! ignite cacheName from b1)
                     (c/putValue! ignite cacheName to b2)
                     (.commit tx)
                     (assoc op :type :ok))))
                (catch org.apache.ignite.transactions.TransactionTimeoutException eTimeOut (info "TransactionTimeoutException") (assoc op :type :fail, :error [:timeout]))
                (catch org.apache.ignite.transactions.TransactionDeadlockException eDeadLock (info "TransactionDeadlockException") (assoc op :type :fail, :error [:deadlock]))
                (catch org.apache.ignite.transactions.TransactionOptimisticException eOptimistic (info "TransactionOptimisticException")
                  (assoc op :type :fail, :error [:optimistic]))
                (catch javax.cache.CacheException e (info "exception") (assoc op :type :fail, :error [:javax.cache.CacheException]))
                (catch java.lang.Exception e2 (info "exception2") (assoc op :type :fail, :error [:java.lang.Exception]))
                (finally (.close tx)))))))

  (teardown! [this test] (.close ignite))
  (close! [this test] (.close ignite)))


(defn bank-read
  "Reads the current state of all accounts without any synchronization."
  [_ _]
  {:type :invoke, :f :read})

(defn bank-transfer
  "Transfers a random amount between two randomly selected accounts."
  [test process]
  (let [n (-> test :client :n)]
    {:type  :invoke
     :f     :transfer
     :value {:from   (long (rand n))
             :to     (long (rand n))
             :amount (+ 1 (long (rand 5)))}}))

(def bank-diff-transfer
  "Like transfer, but only transfers between *different* accounts."
  (gen/filter
   (fn [op]
     (not= (-> op :value :from)
           (-> op :value :to)))
   bank-transfer))

(defn bank-checker
  "Balances must all be non-negative and sum to the model's total."
  []
  (reify
   checker/Checker
   (check [this test model history opts]
          (let [bad-reads (->> history
                               (r/filter op/ok?)
                               (r/filter #(= :read (:f %)))
                               (r/map
                                (fn [op]
                                  (let [balances (:value op)]
                                    (cond (not= (:n model) (count balances))
                                          {:type     :wrong-n
                                           :expected (:n model)
                                           :found    (count balances)
                                           :op       op}

                                          (not= (:total model)
                                                (reduce + balances))
                                          {:type     :wrong-total
                                           :expected (:total model)
                                           :found    (reduce + balances)
                                           :op       op}

                                          (some neg? balances)
                                          {:type  :negative-value
                                           :found balances
                                           :op    op}))))
                               (r/filter identity)
                               (into []))]
            {:valid?    (empty? bad-reads)
             :bad-reads bad-reads}))))

(defn test
  [opts]
  (let [cacheMode                     (get c/cacheModes (:cacheMode opts))
        cacheAtomicityMode            (get c/cacheAtomicityModes (:cacheAtomicityMode opts))
        readFromBackup                (get c/readFromBackups (:readFromBackup opts))
        cacheWriteSynchronizationMode (get c/cacheWriteSynchronizationModes (:cacheWriteSynchronizationMode opts))
        transactionConcurrency        (get c/transactionConcurrencys (:transactionConcurrency opts))
        transactionIsolation          (get c/transactionIsolations (:transactionIsolation opts))]
    (System/setProperty "IGNITE_JVM_PAUSE_DETECTOR_THRESHOLD" "60000")
    (merge tests/noop-test
           {:name      (str "BANK_" (:name opts) "_" (:cacheMode opts) "_" (:cacheAtomicityMode opts) "_" (:readFromBackup opts) "_" (:cacheWriteSynchronizationMode opts) "_" (:transactionConcurrency opts) "_" (:transactionIsolation opts))
            :db        (s/db "2.7.0")
            :client    (BankClient. nil (:cacheName opts) cacheMode cacheAtomicityMode readFromBackup cacheWriteSynchronizationMode transactionConcurrency transactionIsolation (atom false) 5 10)
            :model     {:n 5 :total 50}
            :generator (->> (gen/mix [bank-read bank-diff-transfer])
                            (gen/stagger 1/10)
                            (gen/nemesis nil)
                            (gen/time-limit (:time-limit opts)))
            :checker   (checker/compose
                        {:perf     (checker/perf)
                         :timeline (timeline/html)
                         :details  (bank-checker)})}
           opts)))
