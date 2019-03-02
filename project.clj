(defproject jepsen.ignite "0.1.0-SNAPSHOT"
  :description "Jepsen Ignite Tests"
  :url "https://github.com/map7000/jepsen.ignite"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [jepsen "0.1.11"]
                 [javax.cache/cache-api "1.0.0"]
                 [org.apache.ignite/ignite-core "2.7.0"]
                 [org.apache.ignite/ignite-spring "2.7.0"]
                 [org.apache.ignite/ignite-log4j "2.7.0"]
                 [org.apache.ignite/ignite-indexing "2.7.0"]
                 ]
  :main jepsen.ignite 
  ; :java-source-paths ["src/java"]
  ; :aot [jepsen.client]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
