#!/bin/bash
for cacheMode in PARTITIONED REPLICATED LOCAL
do
  for cacheAtomicityMode in TRANSACTIONAL ATOMIC TRANSACTIONAL_SNAPSHOT
  do
    for cacheWriteSynchronizationMode in FULL_SYNC PRIMARY_SYNC FULL_ASYNC
    do
      for transactionIsolation in SERIALIZABLE READ_COMMITTED REPEATABLE_READ
      do
        for transactionConcurrency in OPTIMISTIC PESSIMISTIC
        do
          for readFromBackup in true false
          do
            eval lein run test --nodes-file nodes --username pprbusr --password pprbusr --concurrency 10 --time-limit 180 --name \\\"Ignite\\\" --cacheMode \\\"$cacheMode\\\" --cacheAtomicityMode \\\"$cacheAtomicityMode\\\" --cacheWriteSynchronizationMode \\\"$cacheWriteSynchronizationMode\\\"  --transactionIsolation \\\"$transactionIsolation\\\" --readFromBackup \\\"$readFromBackup\\\" --transactionConcurrency \\\"$transactionConcurrency\\\"
          done
        done
      done
    done
  done
done
