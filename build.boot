(set-env! :source-paths #{"src" "task"}
          :dependencies '[[org.clojure/clojure "1.6.0" :scope "provided"]
                          [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                          [boot/core "2.0.0-rc4" :scope "provided"]
                          [clojure-msgpack "0.1.0-SNAPSHOT"]])

(import '[java.net Socket])

(require '[clojure.core.async :as async :refer (>!! <!!)]
         '[msgpack.core :refer (pack unpack)])

(require '[exdb.boot-jar :refer :all]
         '[exdb.boot-node :refer :all]
         '[exdb.core :refer :all]
         '[exdb.serf :as serf])

(deftask build
  "Build project"
  [n num NUM int "The number of nodes to create"]
  (comp
   (build-jar)
   (create-nodes :n num)))
