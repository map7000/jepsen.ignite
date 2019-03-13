# jepsen.ignite
Jepsen tests for Apache Ignite


export JEPSEN_ROOT=/home/misha/jepsen.ignite/
./up.sh --dev


lein run test --nodes-file nodes --username * --password * --concurrency 10 --time-limit 120 --name \"Ignite\" --cacheMode \"PARTITIONED\" --cacheAtomicityMode \"TRANSACTIONAL\" --cacheWriteSynchronizationMode \"FULL_SYNC\"  --transactionIsolation \"REPEATABLE_READ\" --readFromBackup \"true\"
