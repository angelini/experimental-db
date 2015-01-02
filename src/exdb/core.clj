(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [exdb.serf :as serf])
  (:gen-class))

(def members (atom {}))

(defn initial-members [client]
  (let [members (serf/members client)]
    (into {} (map (fn [m] [(:name m) (:status m)]) members))))

(defn watch-members [host port]
  (let [client (serf/connect host port)]
    (swap! members (fn [old new] new) (initial-members client))
    (async/go
      (let [event-chan (serf/stream client)]
        (loop [event (<! event-chan)]
          (swap! members (fn [old]
                           (assoc old
                                  (:name (:member event))
                                  (:status (:member event)))))
          (recur (<! event-chan)))))))

(defn -main []
  (watch-members "127.0.0.1" 8200)
  (println "hello world"))
