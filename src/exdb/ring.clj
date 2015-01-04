(ns exdb.ring
  (:import [java.security MessageDigest]))

(defn md5 [string]
  (let [digest-builder (MessageDigest/getInstance "MD5")]
    (.update digest-builder (.getBytes string))
    (clojure.string/join (map #(format "%x" %)
                              (.digest digest-builder)))))

(defn positions [node n]
  (map (fn [i]
         [(md5 (str node i)) node])
       (range n)))

(defn build-ring [nodes n]
  (-> (apply concat (map #(positions % n)
                         nodes))
      sort
      vec))

(defn search [ring key]
  (loop [[hash node] (first ring)
         ring' (rest ring)]
    (cond
      (> (compare hash (md5 key)) 0) node
      (empty? ring') (first (first ring))
      :else (recur (first ring')
                   (rest ring')))))

(defn key->nodes [ring key]
  (search ring key))
