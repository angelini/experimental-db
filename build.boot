(set-env! :source-paths #{"src" "task"}
          :resource-paths #{"src"}
          :dependencies '[[org.clojure/clojure "1.6.0" :scope "provided"]
                          [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                          [clojure-msgpack "0.1.0-SNAPSHOT"]])

(import '[java.net Socket])

(require '[clojure.core.async :as async :refer (>!! <!!)]
         '[msgpack.core :refer (pack unpack)])

(require '[exdb.boot-node :refer :all]
         '[exdb.core :refer :all]
         '[exdb.serf :as serf])

(deftask build
  "Build project"
  []
  (comp
   (aot :namespace '#{exdb.core})
   (pom :project 'exdb
        :version "0.0.1")
   (uber)
   (jar :main 'exdb.core)))
