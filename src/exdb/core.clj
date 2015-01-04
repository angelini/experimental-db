(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [clojurewerkz.chash.ring :as ch]
            [ring.adapter.jetty :refer (run-jetty)]
            [exdb.serf :as serf]
            [exdb.server :as server]
            [exdb.redis :as redis])
  (:gen-class))

(def members (atom {}))

(defn- format-id [id]
  (format "%02d" id))

(defn- id->name [id]
  (str "node-" (format-id id)))

(defn- parse-url [url]
  (let [[host port] (clojure.string/split url #":")]
    [host (Integer/parseInt port)]))

(defn parse-env [env]
  (-> env
      (select-keys ["SERF_SEED_RPC" "NUM_NODES" "NODE_NAME" "API_PORT" "REDIS_PORT"])
      (clojure.set/rename-keys {"SERF_SEED_RPC" :seed-rpc
                                "NUM_NODES" :num
                                "NODE_NAME" :name
                                "API_PORT" :api-port
                                "REDIS_PORT" :redis-port})
      (update-in [:seed-rpc] parse-url)
      (update-in [:num] #(Integer/parseInt %))
      (update-in [:redis-port] #(Integer/parseInt %))
      (update-in [:api-port] #(Integer/parseInt %))))

(defn initial-members [client]
  (let [members (serf/members client)]
    (into {} (map (fn [m] [(:name m) (:status m)]) members))))

(defn watch-members [[host port]]
  (let [client (serf/connect host port)]
    (swap! members (fn [old new] new) (initial-members client))
    (println "members" @members)
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

(defn build-ch-ring [num]
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

(defmulti handle-request
  (fn [req & args] (:command req)))

(defmethod handle-request :get [req ch-ring client]
  (>!! (:res req) {:status 200
                   :body (redis/g client (:key req))}))

(defmethod handle-request :set [req ch-ring client]
  (let [{:keys [key val]} req]
    (>!! (:res req) {:status 200
                     :body (redis/s client key val)})))

(defmethod handle-request :default [req]
  (throw (Exception. (str "Unknown command: " (:command req)))))

(defn start-api [port ch-ring client]
  (let [chan (async/chan 10)]
    (async/go-loop [req (<! chan)]
      (handle-request req ch-ring client)
      (recur (<! chan)))
    (run-jetty (server/api chan) {:port port :join? false})))

(defn -main []
  (let [env (parse-env (System/getenv))
        ch-ring (build-ch-ring (:num env))
        client (redis/connect (:redis-port env))]
    (watch-members (:seed-rpc env))
    (start-api (:api-port env) ch-ring client)))
