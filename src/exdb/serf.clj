(ns exdb.serf
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [msgpack.core :refer (pack unpack)])
  (:import [java.net Socket SocketTimeoutException]))

(def seq-counter (atom 0))

(defn- read-socket [in]
  (try
    (let [first (.read in)]
      (loop [acc [first]]
        (if-not (zero? (.available in))
          (recur (conj acc (.read in)))
          (seq acc))))
    (catch SocketTimeoutException e nil)))

(defn connect [host port]
  (Socket. host port))

(defn read-messages [s]
  (let [in (.getInputStream s)
        chan (async/chan 10)]
    (async/go
      (loop [messages (read-socket in)]
        (let [obj (unpack messages)
              byte-count (count (pack obj))
              other-messages (drop byte-count messages)]
          (>! chan obj)
          (if (> (count other-messages) 0)
            (recur other-messages)
            (recur (read-socket in))))))
    chan))

(defn write-command [s b]
  (.write (.getOutputStream s) b 0 (count b)))

(defn send
  ([s command] (write-command s (pack {"Command" command "Seq" (swap! seq-counter inc)})))
  ([s command body] (write-command s (-> (pack {"Command" command "Seq" (swap! seq-counter inc)})
                                       (concat (pack body))
                                       byte-array))))
