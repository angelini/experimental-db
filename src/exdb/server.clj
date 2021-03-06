(ns exdb.server
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [compojure.core :refer :all]
            [ring.middleware.defaults :refer (wrap-defaults api-defaults)]
            [ring.middleware.json :refer (wrap-json-body wrap-json-response)]))

(defn get-key [key req]
  (let [{:keys [chan body]} req
        res-chan (async/chan)]
    (>!! chan {:command :get
               :key key
               :res res-chan})
    (let [res (<!! res-chan)]
      (async/close! res-chan)
      res)))

(defn set-key [key req]
  (let [{:keys [chan body]} req
        res-chan (async/chan)]
    (>!! chan {:command :set
               :key key
               :val body
               :res res-chan})
    (let [res (<!! res-chan)]
      (async/close! res-chan)
      res)))

(defroutes api-routes
  (GET  "/get/:key" [key :as req] (get-key key req))
  (POST "/set/:key" [key :as req] (set-key key req)))

(defn add-chan [handler chan]
  (fn [req]
    (prn "add-chan" req)
    (handler (assoc req :chan chan))))

(defn api [chan]
  (-> api-routes
      (wrap-defaults api-defaults)
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (add-chan chan)))
