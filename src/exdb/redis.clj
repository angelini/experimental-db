(ns exdb.redis
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [taoensso.carmine :as car :refer (wcar)]))

(defn s [c key val]
  (wcar c (car/set key val)))

(defn g [c key]
  (wcar c (car/get key)))

(defn connect [port]
  {:pool {} :spec {:host "127.0.0.1" :port port}})
