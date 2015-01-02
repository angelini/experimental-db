(ns exdb.boot-build
  (:require [boot.core :refer :all]
            [boot.task.built-in :refer :all]
            [clojure.java.io :as io]))


(deftask build
  "Build project"
  []
  (comp
   (aot :namespace '#{exdb.core})
   (pom :project 'exdb
        :version "0.0.1")
   (uber)
   (jar :main 'exdb.core)))
