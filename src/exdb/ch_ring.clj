(ns exdb.ch-ring
  (:import [java.security MessageDigest]
           [java.math BigInteger]))

(def ring-top (dec (Math/pow 2 160)))

(defn ring-increment [n]
  (quot ring-top n))

(defn sha [s]
  (-> (MessageDigest/getInstance "SHA-1")
      (.digest (.getBytes s))
      (->> (BigInteger. 1))
      double))

(defn build-ring [n-partitions seed]
  (let [delta (ring-increment n-partitions)
        tokens (doall (range 0 ring-top delta))]
    (vec (map #(identity [% seed])
              tokens))))

(defn at-node? [ring n-token token]
  (let [increment (ring-increment (count ring))]
    (and (>= token n-token)
         (not (>= token (+ n-token
                           increment))))))

(defn update-partition [ring token node]
  (loop [idx 0
         [[n-token _] & parts] ring]
    (if (at-node? ring n-token token)
      (assoc ring idx [token node])
      (recur (+ idx 1)
             parts))))

(defn unique-nodes [ring]
  (reduce (fn [nodes [_ node]]
            (conj nodes node))
          #{}
          ring))

(defn key->nodes [ring key n]
  (assert (>= (count (unique-nodes ring)) n))
  (let [token (sha key)
        c-ring (cycle ring)
        increment (ring-increment (count ring))]
    (reduce (fn [nodes [n-token node]]
              (cond
                (= (count nodes) n) (reduced nodes)
                (not (empty? nodes)) (conj nodes node)
                (at-node? ring n-token token) (conj nodes node)
                :else nodes))
            #{}
            c-ring)))
