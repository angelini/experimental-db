(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [exdb.serf :as serf])
  (:gen-class))

(def members (atom {}))

(defn initial-members [s chan]
  (let [members (serf/members s chan)]
    (into {} (map (fn [m] [(:name m) (:status m)]) members))))

(defn watch-members [host port]
  (let [s (serf/connect host port)
        chan (serf/read-messages s)]
    (serf/handshake s chan)
    (swap! members (fn [old new] new) (initial-members s chan))
    (async/go
      (let [event-chan (serf/stream s chan)]
        (loop [event (<! event-chan)]
          (swap! members (fn [old]
                           (assoc old
                                  (:name (:member event))
                                  (:status (:member event)))))
          (recur (<! event-chan)))))))

(defn -main []
  (watch-members "127.0.0.1" 8200)
  (println "hello world"))
