(ns exdb.serf
  (:require [clojure.core.async :as async :refer (>!! <!! >! <!)]
            [msgpack.core :refer (pack unpack)])
  (:import [java.net Socket SocketTimeoutException]))

(def seq-counter (atom 0))

(defn- throw-on-error [header]
  (let [err (header "Error")]
    (when (not (empty? err))
      (throw (Exception. (str "RPC Error: " err))))))

(defn- read-socket [in]
  (try
    (let [first (.read in)]
      (loop [acc [first]]
        (if-not (zero? (.available in))
          (recur (conj acc (.read in)))
          (seq acc))))
    (catch SocketTimeoutException e nil)))

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

(defn- create-message [command body]
  (let [seq-num (swap! seq-counter inc)
        header {"Command" command "Seq" seq-num}]
    (if (nil? body)
      (pack header)
      (-> (pack header)
          (concat (pack body))
          byte-array))))

(defn- write-command [s command & {:keys [body]}]
  (let [message (create-message command body)]
    (.write (.getOutputStream s) message 0 (count message))))

(defn- member-parser [member]
  (-> member
    (select-keys ["Name" "Status"])
    (clojure.set/rename-keys {"Name" :name "Status" :status})))

(defn- handshake [{socket :socket chan :chan}]
  (write-command socket "handshake" :body {"Version" 1})
  (throw-on-error (<!! chan)))

(defn members [{socket :socket chan :chan}]
  (write-command socket "members")
  (throw-on-error (<!! chan))
  (let [res (<!! chan)]
    (map member-parser (res "Members"))))

(defn stream [{socket :socket chan :chan}]
  (write-command socket "stream" :body {"Type" "*"})
  (throw-on-error (<!! chan))
  (let [event-chan (async/chan 10)]
    (async/go
      (loop [header (<! chan)
             event (<! chan)]
        (throw-on-error header)
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
