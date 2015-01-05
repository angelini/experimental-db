(set-env! :source-paths #{"src" "task"}
          :dependencies '[[org.clojure/clojure "1.6.0" :scope "provided"]
                          [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                          [boot/core "2.0.0-rc4" :scope "provided"]
                          [ring "1.3.2"]
                          [ring/ring-json "0.3.1"]
                          [ring/ring-defaults "0.1.3"]
                          [compojure "1.3.1"]
                          [clojure-msgpack "0.1.0-SNAPSHOT"]
                          [com.taoensso/carmine "2.9.0"]])

(import '[java.net Socket])

(require '[clojure.java.shell :only (sh)]
         '[clojure.java.io :as io]
         '[boot.pod :as pod]
         '[clojure.core.async :as async :refer (>!! <!!)]
         '[msgpack.core :refer (pack unpack)])

(require '[exdb.boot-jar :refer :all]
         '[exdb.boot-node :refer :all]
         '[exdb.core :refer :all]
         '[exdb.serf :as serf]
         '[exdb.redis :as redis]
         '[exdb.ch-ring :as ch])

(defn exec [script & args]
  (let [file (-> (get-env :target-path)
                 (io/file script))
        path (.getPath file)]
    (when (.exists file)
      (sh "chmod" "a+x" path)
      (apply sh (conj args path)))))

(deftask build
  "Build project"
  [n num NUM int "The number of nodes to create"]
  (set-env! :target-path "target")
  (comp (build-jar)
        (create-nodes :num num)))

(deftask start
  "Start all nodes"
  []
  (with-post-wrap fileset
    (println "Starting nodes...")
    (exec "all" "start")))

(deftask stop
  "Stop all nodes"
  []
  (with-pre-wrap fileset
    (println "Stopping nodes...")
    (exec "all" "stop")
    fileset))

(deftask run
  "Run a specific node"
  [i id ID int "The node id"]
  (let [name (id->name id)
        script (str (id->name id) "/exdb")]
    (with-pre-wrap fileset
      (exec script "run"))))

(deftask all
  "Restart and rebuilt all nodes"
  [n num NUM int "The number of nodes to create"]
  (comp (stop)
        (build :num num)
        (start)))
