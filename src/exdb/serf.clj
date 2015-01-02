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

(defn- write-socket [s b]
  (.write (.getOutputStream s) b 0 (count b)))

(defn- read-messages [s]
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

(defn- write-command
  ([s command] (write-socket s (pack {"Command" command "Seq" (swap! seq-counter inc)})))
  ([s command body] (write-socket s (-> (pack {"Command" command "Seq" (swap! seq-counter inc)})
                                      (concat (pack body))
                                      byte-array))))

(defn- member-parser [member]
  (-> member
    (select-keys ["Name" "Status"])
    (clojure.set/rename-keys {"Name" :name "Status" :status})))

(defn- handshake [{socket :socket chan :chan}]
  (write-command socket "handshake" {"Version" 1})
  (<!! chan))

(defn members [{socket :socket chan :chan}]
  (write-command socket "members")
  (<!! chan)
  (let [res (<!! chan)]
    (map member-parser (res "Members"))))

(defn stream [{socket :socket chan :chan}]
  (write-command socket "stream" {"Type" "*"})
  (<!! chan)
  (let [event-chan (async/chan 10)]
    (async/go
      (loop [header (<! chan)
             event (<! chan)]
        (>! event-chan {:event (event "Event") :member (-> (event "Members")
                                                         first
                                                         member-parser)})
        (recur (<! chan)
               (<! chan))))
    event-chan))

(defn connect [host port]
  (let [socket (Socket. host port)
        client {:socket socket
                :chan (read-messages socket)}]
    (handshake client)
    client))
