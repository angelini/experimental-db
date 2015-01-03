(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [exdb.serf :as serf])
  (:gen-class))

(def members (atom {}))

(defn initial-members [client]
  (let [members (serf/members client)]
    (into {} (map (fn [m] [(:name m) (:status m)]) members))))

(defn watch-members [seed]
  (let [[host port] (clojure.string/split seed #":")
        client (serf/connect host (Integer/parseInt port))]
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
  (watch-members (System/getenv "SERF_SEED_RPC"))
  (println "members" @members))
