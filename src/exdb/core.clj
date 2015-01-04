(ns exdb.core
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [ring.adapter.jetty :refer (run-jetty)]
            [ring.util.response :refer (response redirect)]
            [exdb.serf :as serf]
            [exdb.server :as server]
            [exdb.redis :as redis]
            [exdb.ring :as ring])
  (:gen-class))

(def members (atom {}))

(defn- parse-addr [addr]
  (let [[host port] (clojure.string/split addr #":")]
    [host (Integer/parseInt port)]))

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
  (>!! (:res req) (-> (rand-nth nodes)
                      (str "/key/" (:key req))
                      redirect)))

(defn listen [chan api-addr ring client]
  (async/go-loop [req (<! chan)]
    (let [nodes (ring/key->nodes ring (:key req))]
      (if (contains? nodes api-addr)
        (handle-request req ring client)
        (redirect-to-node req nodes)))))

(defn start-api [api-addr ring client]
  (let [chan (async/chan 10)
        port (second api-addr)]
    (listen chan api-addr ring client)
    (run-jetty (server/api chan) {:port port :join? false})))

(defn -main []
  (let [env (parse-env (System/getenv))
        client (redis/connect (:redis-addr env))
        ring (ring/build-ring [(:api-addr env)] (:n env))]
    (watch-members (:seed-rpc env) ring)
    (start-api (:api-addr env) ring client)))
