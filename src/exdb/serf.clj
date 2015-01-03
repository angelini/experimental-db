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

(defn- update-count [current-count new-count outputs]
  (dosync
   (let [current @current-count
         keep? (:keep? (get outputs current))]
     (alter current-count (fn [old new] new) new-count)
     (when (not keep?)
       (alter outputs dissoc current)))))

(defn- read-messages [{socket :socket outputs :outputs}]
  (let [in (.getInputStream socket)
        current-count (ref 0)]
    (async/go
      (loop [messages (read-socket in)]
        (let [obj (unpack messages)
              byte-count (count (pack obj))
              other-messages (drop byte-count messages)]
          (if-let [new-count (get obj "Seq")]
            (update-count current-count new-count outputs))
          (>! (get outputs @current-count) obj)
          (if (> (count other-messages) 0)
            (recur other-messages)
            (recur (read-socket in))))))))

(defn- create-message [count command body]
  (let [header {"Command" command "Seq" count}]
    (if (nil? body)
      (pack header)
      (-> (pack header)
          (concat (pack body))
          byte-array))))

(defn- throw-on-error [header]
  (let [err (header "Error")]
    (when err
      (throw (Exception. (str "RPC Error: " err))))))

(defn- attach-output-chan! [client count output]
  (dosync (alter (:outputs client) assoc count output)))

(defn- write-socket [client count b chan keep?]
  (let [socket (:socket client)]
    (attach-output-chan! client count {:chan chan
                                       :keep? keep?})
    (.write (.getOutputStream socket) b 0 (count b))))

(defn- write-command [client command & {:keys [body res? keep?]}]
   (let [chan (async/chan 2)
         count (swap! seq-counter inc)
         message (create-message count command body)]
     (async/go
       (write-socket client count message chan keep?)
       (throw-on-error (<! chan))
       (if res?
         (let [res (<! chan)]
           (when (not keep?) (async/close! chan))
           res)
         (when (not keep?) (async/close! chan))))))

(defn- member-parser [member]
  (-> member
    (select-keys ["Name" "Status"])
    (clojure.set/rename-keys {"Name" :name "Status" :status})))

(defn handshake [client]
  (<!! (write-comamnd socket "handshake" :body {"Version" 1})))

(defn members [client]
  (let [chan (write-command client "members" :res? true)]
    (map member-parser ((<!! chan) "Members"))))

(defn stream [client]
  (write-command socket "stream" {"Type" "*"})
  (throw-on-error (<!! chan))
  (let [event-chan (async/chan 10)]
    (async/go
      (let [chan (write-command client "stream" :body {"Type" "*"} :res? true :keep? true)]
        (loop [header (<! chan)
               event (<! chan)]
          (throw-on-error header)
          (>! event-chan {:event (event "Event") :member (-> (event "Members")
                                                             first
                                                             member-parser)})
          (recur (<! chan)
                 (<! chan)))))
    event-chan))

(defn connect [host port]
  (let [socket (Socket. host port)
        client {:socket socket
                :outputs (ref {})}]
    (read-messages client)
    (handshake client)
    client))
