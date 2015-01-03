(ns exdb.boot-jar
  (:require [boot.core :refer :all]
            [boot.task.built-in :refer :all]
            [clojure.java.io :as io]))


(deftask build-jar
  "Build jar"
  []
  (comp
   (aot :namespace '#{exdb.core})
   (pom :project 'exdb
        :version "0.0.1")
   (uber)
   (jar :file "exdb.jar"
        :main 'exdb.core)))
