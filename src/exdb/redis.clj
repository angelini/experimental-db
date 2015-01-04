(ns exdb.redis
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [taoensso.carmine :as car :refer (wcar)]))

(defn s [c key val]
  (wcar c (car/set key val)))

(defn g [c key]
  (wcar c (car/get key)))

(defn connect [[host port]]
  {:pool {} :spec {:host host :port port}})
