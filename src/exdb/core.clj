(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [exdb.serf :as serf])
  (:gen-class))

(def members (atom {}))

(defn -main []
  (println "hello world"))
