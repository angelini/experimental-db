(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [exdb.serf :as serf]
            [clojurewerkz.chash.ring :as ch])
  (:gen-class))

(def members (atom {}))

(defn- format-id [id]
  (format "%02d" id))

(defn- id->name [id]
  (str "node-" (format-id id)))

(defn parse-env [env]
  (-> env
      (select-keys ["SERF_SEED_RPC" "NUM_NODES" "NODE_NAME"])
      (clojure.set/rename-keys {"SERF_SEED_RPC" :seed_rpc
                                "NUM_NODES" :num
                                "NODE_NAME" :name})
      (update-in [:seed_rpc] (fn [seed]
                               (let [[host port] (clojure.string/split seed #":")]
                                 [host (Integer/parseInt port)])))
      (update-in [:num] (fn [num]
                          (Integer/parseInt num)))))

(defn initial-members [client]
  (let [members (serf/members client)]
    (into {} (map (fn [m] [(:name m) (:status m)]) members))))

(defn watch-members [[host port]]
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

(defn mark-claimant [idx ring-elem num]
  (let [[hash _] ring-elem
        id (mod idx num)
        name (id->name id)]
    [hash name]))

(defn build-ring [num]
  (let [ring (ch/fresh 64 "seed")
        claims (map-indexed #(mark-claimant %1 %2 num) (ch/claims ring))]
    (reduce (fn [r [hash name]]
              (ch/update r hash name))
            ring
            claims)))

(defn key->nodes [ring key]
  (let [ring-key (ch/key-of key)
        successors (ch/successors ring ring-key 3)]
    (map #(nth % 1) successors)))

(defn -main []
  (let [env (parse-env (System/getenv))
        ring (build-ring (:num env))]
    (watch-members (:seed_rpc env))
    (println "members" @members)
    (println "ring" ring)))
