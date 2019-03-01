(ns test.core
    (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (print (iterator-seq (.iterator (.keySet (java.lang.System/getProperties)))))
  )