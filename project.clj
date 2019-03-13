(defproject jepsen.ignite "0.1.0-SNAPSHOT"
  :description "Jepsen Ignite Tests"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [jepsen "0.1.11"]
                 [javax.cache/cache-api "1.0.0"]
                 [org.apache.ignite/ignite-core "2.7.0"]
                 [org.apache.ignite/ignite-spring "2.7.0"]
                 [org.apache.ignite/ignite-log4j "2.7.0"]
                 [org.apache.ignite/ignite-indexing "2.7.0"]
                 ]
  ;:local-repo "/gridgain/sas/sbt-filatov-myu/maven"
  ;:repositories { "local" ~(str (.toURL (java.io.File. "/gridgain/sas/sbt-filatov-myu/maven")))}
  :local-repo "/gridgain/sas/sbt-filatov-myu/maven"
  :repositories { "local" ~(str (.toURL (java.io.File. "/gridgain/sas/sbt-filatov-myu/maven")))}
  :main jepsen.ignite 
  ; :java-source-paths ["src/java"]
  ; :aot [jepsen.client]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
