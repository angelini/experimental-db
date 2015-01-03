(set-env! :source-paths #{"src" "task"}
          :dependencies '[[org.clojure/clojure "1.6.0" :scope "provided"]
                          [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                          [boot/core "2.0.0-rc4" :scope "provided"]
                          [ring "1.3.2"]
                          [ring/ring-json "0.3.1"]
                          [ring/ring-defaults "0.1.3"]
                          [compojure "1.3.1"]
                          [clojure-msgpack "0.1.0-SNAPSHOT"]
                          [clojurewerkz/chash "1.1.0"]])

(import '[java.net Socket])

(require '[clojure.core.async :as async :refer (>!! <!!)]
         '[msgpack.core :refer (pack unpack)]
         '[clojurewerkz.chash.ring :as ch])

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
