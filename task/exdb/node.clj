(ns exdb.node
  (:require [boot.core :refer :all]
            [clojure.java.io :as io]))

(defn clj->env [[k v]]
  (str k "=" v))

(defn write-env [dir id name]
  (let [file (io/file dir "env.sh")
        env (map clj->env {"NODE_NAME" name
                           "SERF_SEED" "127.0.0.1:8100"
                           "SERF_BIND" (str "127.0.0.1:81" id)
                           "SERF_RPC" (str "127.0.0.1:82" id)
                           "REDIS_PORT" (str "83" id)})]
    (spit file (clojure.string/join "\n" env))))

(defn copy-script [dir script]
  (let [file (io/file dir "exdb")
        content (slurp script)]
    (spit file content)
    (.setExecutable file true)))

(deftask create-nodes
  "Create a new node"
  [n num NUM int "The number of nodes to create"]
  (let [tmp (temp-dir!)]
    (with-pre-wrap fileset
      (empty-dir! tmp)
      (doseq [id (-> num
                   (or 3)
                   range)
              :let [id-fmt (format "%02d" id)
                    name (str "node-" id-fmt)
                    node-dir (io/file tmp name)
                    script (->> (input-files fileset)
                             (filter #(= (:path %) "exdb.sh"))
                             first
                             tmpfile)]]
        (.mkdir node-dir)
        (write-env node-dir id-fmt name)
        (copy-script node-dir script))
      (-> fileset
        (add-resource tmp)
        (commit!)))))
