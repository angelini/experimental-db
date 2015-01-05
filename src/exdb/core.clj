(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [ring.adapter.jetty :refer (run-jetty)]
            [ring.util.response :refer (response redirect)]
            [exdb.serf :as serf]
            [exdb.server :as server]
            [exdb.redis :as redis]
            [exdb.ch-ring :as ch])
  (:gen-class))

(def members (atom {}))

(defn- parse-addr [s]
  (let [[host port] (clojure.string/split s #":")]
    [host (Integer/parseInt port)]))

(defn- addr->str [[host port]]
  (str host ":" port))

(defn parse-env [env]
  (-> env
      (select-keys ["SERF_SEED_RPC" "NUM_NODES" "NODE_NAME" "API_ADDR" "REDIS_ADDR"])
      (clojure.set/rename-keys {"SERF_SEED_RPC" :seed-rpc
                                "NUM_NODES" :num
                                "NODE_NAME" :name
                                "API_ADDR" :api-addr
                                "REDIS_ADDR" :redis-addr})
      (update-in [:seed-rpc] parse-addr)
      (update-in [:api-addr] parse-addr)
      (update-in [:redis-addr] parse-addr)
      (update-in [:num] #(Integer/parseInt %))))

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

(defmulti handle-request
  (fn [req & args] (:command req)))

(defmethod handle-request :get [req ring client]
  (>!! (:res req) (-> (redis/g client (:key req))
                      response)))

(defmethod handle-request :set [req ring client]
  (let [{:keys [key val]} req]
    (>!! (:res req) (-> (redis/s client key val)
                        response))))

(defmethod handle-request :default [req]
  (throw (Exception. (str "Unknown command: " (:command req)))))

(defn redirect-to-node [req nodes]
  (let [node (rand-nth (vec nodes))
        {:keys [command key]} req]
    (>!! (:res req) (-> (str "http://" node "/" command "/" key)
                        redirect))))

(defn mark-claimant [nodes idx part]
  (let [[hash _] part
        addrs (vec nodes)
        addr (nth addrs (mod idx (count nodes)))]
    [hash addr]))

(defn update-ring [ring nodes]
  (let [claims (map-indexed #(mark-claimant nodes %1 %2) ring)]
    (reduce (fn [r [token node]]
              (ch/update-partition r token node))
            ring
            claims)))

(defn listen [chan api-addr ring client]
  (async/go-loop [req (<! chan)]
    (let [ring' (update-ring ring (keys @members))
          nodes (ch/key->nodes ring' (:key req) 3)]
      (if (contains? nodes (addr->str api-addr))
        (handle-request req ring' client)
        (redirect-to-node req nodes)))))

(defn start-api [api-addr ring client]
  (let [chan (async/chan 10)
        port (second api-addr)]
    (listen chan api-addr ring client)
    (run-jetty (server/api chan) {:port port})))

(defn -main []
  (let [env (parse-env (System/getenv))
        client (redis/connect (:redis-addr env))
        ring (ch/build-ring (:num env) (:api-addr env))]
    (watch-members (:seed-rpc env))
    (start-api (:api-addr env) ring client)))
