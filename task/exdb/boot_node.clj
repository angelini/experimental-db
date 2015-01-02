(ns exdb.boot-node
  (:require [boot.core :refer :all]
            [clojure.java.io :as io]
            [exdb.boot-build :refer (build)]))

(defn- clj->env [[k v]]
  (str k "=" v))

(defn- format-id [id]
  (format "%02d" id))

(defn- id->name [id]
  (str "node-" (format-id id)))

(defn create-node-env [dir id]
  (let [out (io/file dir "env.sh")
        id-fmt (format-id id)
        env (map clj->env {"NODE_NAME" (id->name id)
                           "SERF_SEED" "127.0.0.1:8100"
                           "SERF_BIND" (str "127.0.0.1:81" id-fmt)
                           "SERF_RPC" (str "127.0.0.1:82" id-fmt)
                           "REDIS_PORT" (str "83" id-fmt)})]
    (spit out (str (clojure.string/join "\n" env) "\n"))))

(defn copy-file [fs dir script name]
  (let [script (->> (input-files fs)
                    (by-name [script])
                    first
                    tmpfile)]
    (spit (io/file dir name)
          (slurp script))))

(deftask create-nodes
  "Create a new node"
  [n num NUM int "The number of nodes to create"]
  (let [tmp (temp-dir!)
        ids (map inc (-> num
                         (or 3)
                         range))]
    (with-pre-wrap fileset
      (empty-dir! tmp) ;; TODO only remove dirs like "node-\d+"
      (doseq [id ids
              :let [name (id->name id)
                    node-dir (io/file tmp name)]]
        (.mkdir node-dir)
        (copy-file fileset node-dir "exdb.sh" "exdb")
        (create-node-env node-dir id))
      (-> fileset
          (add-resource tmp)
          commit!))))
