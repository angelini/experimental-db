(ns exdb.boot-node
  (:require [boot.core :refer :all]
            [clojure.java.io :as io]))

(defn- clj->env [[k v]]
  (str "export " k "=" v))

(defn- format-id [id]
  (format "%02d" id))

(defn- id->name [id]
  (str "node-" (format-id id)))

(defn create-node-env [dir id num]
  (let [out (io/file dir "env.sh")
        id-fmt (format-id id)
        env (map clj->env {"NODE_NAME" (id->name id)
                           "SERF_SEED" "127.0.0.1:8100"
                           "SERF_SEED_RPC" "127.0.0.1:8200"
                           "SERF_BIND" (str "127.0.0.1:81" id-fmt)
                           "SERF_RPC" (str "127.0.0.1:82" id-fmt)
                           "REDIS_PORT" (str "83" id-fmt)
                           "NUM_NODES" (str num)})]
    (spit out (str (clojure.string/join "\n" env) "\n"))))

(defn copy-file
  ([files dir name] (copy-file files dir name name))
  ([files dir name output]
   (let [file (->> files
                   (by-name [name])
                   first
                   tmpfile)]
     (io/copy file (io/file dir output)))))

(deftask create-nodes
  "Create a new node"
  [n num NUM int "The number of nodes to create"]
  (let [tmp (temp-dir!)
        ids (-> num
                (or 3)
                range)]
    (with-pre-wrap fileset
      (empty-dir! tmp)
      (doseq [id ids
              :let [name (id->name id)
                    node-dir (io/file tmp name)]]
        (println (str "Creating " name "..."))
        (.mkdir node-dir)
        (copy-file (output-files fileset) node-dir "exdb.jar")
        (copy-file (input-files fileset) node-dir "exdb.sh" "exdb")
        (create-node-env node-dir id (count ids)))
      (println "Copying all.sh...")
      (copy-file (input-files fileset) tmp "all.sh" "all")
      (-> fileset
          (add-resource tmp)
          commit!))))
