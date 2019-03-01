(ns jepsen.ignite.bank
    "Simulates transfers between bank accounts"
    (:refer-clojure :exclude
                    [test])
    (:require [jepsen
               [client :as client]
               [tests :as tests]
               [checker :as checker]
               [generator :as gen]
               [independent :as independent]
               [reconnect :as rc]
               [util :as util
                :refer   [meh]]]
              [jepsen.checker.timeline :as timeline]
              [jepsen.ignite.client :as c]
              [clojure.core.reducers :as r]
              [jepsen.ignite.support :as s]
              [clojure.tools.logging :refer :all]
              [knossos.model :as model]
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
    (assoc this :ignite (c/startClient!)))
  (setup! [this test]
    (info "Start setup")
    (locking tbl-created?
             (when (compare-and-set! tbl-created? false true)
                   (info "Setup")
                   ;                   (Thread/sleep 1000)
                   (c/destroyCache! ignite cacheName)
                   ;                   (Thread/sleep 1000)
                   (info "Creating table")
                   (c/createCache! ignite cacheName cacheMode cacheAtomicityMode cacheWriteSynchronizationMode readFromBackup)
                   (dotimes [i n]
                     ;                     (Thread/sleep 500)
                     (info "Creating account" i starting-balance)
                     (c/putValue! ignite cacheName i starting-balance)))))

  (invoke! [this test op]
    (case (:f op)
          :read (let [value (c/getCacheValues ignite cacheName)]
                  (assoc op :type :ok :value value))

          :transfer
          (let [{:keys [from to amount]} (:value op)
                b1                       (- (c/getValue ignite cacheName from) amount)
                b2                       (+ (c/getValue ignite cacheName to) amount)]
            (cond
             (neg? b1)
             (assoc op :type :fail, :error [:negative from b1])
             (neg? b2)
             (assoc op :type :fail, :error [:negative to b2])
             true
             (do (c/putValue! ignite cacheName from b1)
               (c/putValue! ignite cacheName to b2)
               (assoc op :type :ok))))))

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
     :value {:from   (rand-int n)
             :to     (rand-int n)
             :amount (+ 1 (rand-int 5))}}))

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

;(defn bank-test-base
;  [opts]
;  (cockroach/basic-test
;   (merge
;    {:client      {:client (:client opts)
;                   :during (->> (gen/mix [bank-read bank-diff-transfer])
;                                (gen/clients))
;
;                   :final (gen/clients (gen/once bank-read))}
;     :checker     (checker/compose
;                   {:perf    (checker/perf)
;                    :timeline (timeline/html)
;                    :details (bank-checker)})}
;    (dissoc opts :client))))

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
            :during    (->> (gen/mix [bank-read bank-diff-transfer])
                            (gen/clients))

            :final     (gen/clients (gen/once bank-read))
            :generator (->> (gen/mix [bank-read bank-diff-transfer])
                            (gen/stagger 1/50)
                            (gen/nemesis nil)
                            (gen/time-limit (:time-limit opts)))
            :checker   (checker/compose
                        {:perf     (checker/perf)
                         :timeline (timeline/html)
                         :details  (bank-checker)
                         :model    {:n 5 :total 50}})}
           opts)))

; One bank account per table
;(defrecord MultiBankClient [tbl-created? n starting-balance conn]
;  client/Client
;  (open! [this test node]
;    (assoc this :conn (c/client node)))
;
;  (setup! [this test]
;    (locking tbl-created?
;             (when (compare-and-set! tbl-created? false true)
;                   (c/with-conn [c conn]
;                                (dotimes [i n]
;                                  (Thread/sleep 500)
;                                  (c/with-txn-retry
;                                   (j/execute! c [(str "drop table if exists accounts" i)]))
;                                  (Thread/sleep 500)
;                                  (info "Creating table " i)
;                                  (c/with-txn-retry
;                                   (j/execute! c [(str "create table accounts" i
;                                                       " (balance bigint not null)")]))
;                                  (Thread/sleep 500)
;                                  (info "Populating account" i)
;                                  (c/with-txn-retry
;                                   (c/insert! c (str "accounts" i) {:balance starting-balance})))))))
;
;  (invoke! [this test op]
;    (c/with-exception->op op
;                          (c/with-conn [c conn]
;                                       (c/with-timeout
;                                        (c/with-txn-retry
;                                         (c/with-txn [c c]
;                                                     (case (:f op)
;                                                           :read
;                                                           (->> (range n)
;                                                                (mapv (fn [x]
;                                                                        (->> (c/query
;                                                                              c [(str "select balance from accounts" x)]
;                                                                              {:row-fn :balance})
;                                                                             first)))
;                                                                (assoc op :type :ok, :value))
;
;                                                           :transfer
;                                                           (let [{:keys [from to amount]} (:value op)
;                                                                 from (str "accounts" from)
;                                                                 to   (str "accounts" to)
;                                                                 b1 (-> c
;                                                                        (c/query
;                                                                         [(str "select balance from " from)]
;                                                                         {:row-fn :balance})
;                                                                        first
;                                                                        (- amount))
;                                                                 b2 (-> c
;                                                                        (c/query [(str "select balance from " to)]
;                                                                                 {:row-fn :balance})
;                                                                        first
;                                                                        (+ amount))]
;                                                             (cond (neg? b1)
;                                                                   (assoc op :type :fail, :error [:negative from b1])
;
;                                                                   (neg? b2)
;                                                                   (assoc op :type :fail, :error [:negative to b2])
;
;                                                                   true
;                                                                   (do (c/update! c from {:balance b1} [])
;                                                                     (c/update! c to   {:balance b2} [])
;                                                                     (assoc op :type :ok)))))))))))
;
;  (teardown! [this test](.close ignite))
;  (close! [this test](.close ignite)))
;
;(defn multitable-test
;  [opts]
;  (bank-test-base
;   (merge {:name   "bank-multitable"
;           :model  {:n 5 :total 50}
;           :client (MultiBankClient. (atom false) 5 10 nil)}
;          opts)))
