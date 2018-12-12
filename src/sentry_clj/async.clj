(ns sentry-clj.async
  (:require [clojure.string :as clj-string]
            [clojure.tools.logging :as clj-logging]
            [raven-clj.core :as raven]
            [raven-clj.interfaces :as raven-interfaces])
  (:import [clojure.lang ExceptionInfo]
           [com.google.common.util.concurrent ThreadFactoryBuilder]
           [com.google.common.util.concurrent MoreExecutors]
           [java.util UUID]
           [java.util.concurrent ArrayBlockingQueue]
           [java.util.concurrent ExecutorService]
           [java.util.concurrent ThreadPoolExecutor]
           [java.util.concurrent ThreadPoolExecutor$DiscardPolicy]
           [java.util.concurrent TimeUnit]))

(defn- error->message
  [error]
  (when (instance? ExceptionInfo error)
    ["\nData:" (ex-data error)]))

(defn- make-uncaught-exception-handler
  [logger-fn]
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_this _thread exception]
      (logger-fn :error exception "an uncaught exception occurred while allocating executor worker"))))

(defn- make-thread-factory
  [thread-name-format logger-fn]
  (-> (ThreadFactoryBuilder.)
      (.setDaemon true)
      (.setNameFormat thread-name-format)
      (.setUncaughtExceptionHandler (make-uncaught-exception-handler logger-fn))
      (.build)))

(defn- make-work-queue
  [queue-size]
  (ArrayBlockingQueue. queue-size))

(defn- make-rejected-execution-handler
  []
  (ThreadPoolExecutor$DiscardPolicy.))

(defn- ^ExecutorService make-executor
  [owner-id
   logger-fn
   {:keys [async?
           async-executor-workers-idle-count
           async-executor-workers-maximum-count
           async-executor-workers-keep-alive-time
           async-executor-workers-keep-alive-unit
           async-executor-workers-queue-size
           async-executor-workers-name-format]
    :or   {async?                                 false
           async-executor-workers-idle-count      10
           async-executor-workers-maximum-count   10
           async-executor-workers-keep-alive-time 0
           async-executor-workers-keep-alive-unit TimeUnit/MILLISECONDS
           async-executor-workers-queue-size      10}}]
  (if async?
    (let [async-executor-workers-name-format (or async-executor-workers-name-format
                                                 (str "corvus-" owner-id "-worker-%d"))]
      (ThreadPoolExecutor. async-executor-workers-idle-count
                           async-executor-workers-maximum-count
                           async-executor-workers-keep-alive-time
                           async-executor-workers-keep-alive-unit
                           (make-work-queue async-executor-workers-queue-size)
                           (make-thread-factory async-executor-workers-name-format logger-fn)
                           (make-rejected-execution-handler)))
    (MoreExecutors/newDirectExecutorService)))

(defn make-captor
  ([sentry
    application]
   (make-captor sentry application {}))
  ([{:keys [logger-fn]
     :or   {logger-fn (fn [level
                           message
                           & more]
                        (clj-logging/logp level message more))}
     :as   sentry}
    application
    options]
   (let [captor-id (UUID/randomUUID)]
     (logger-fn :info (format "initializing captor[%s] executor" captor-id) "_/\\__/\\__0>")
     (-> {:id captor-id}
         (assoc :sentry (assoc sentry :logger-fn logger-fn))
         (assoc :application application)
         (assoc :executor (make-executor captor-id logger-fn options))))))

(defn kill-captor
  ([captor]
   (kill-captor captor {}))
  ([{captor-id :id
     :keys     [sentry
                executor]}
    {:keys [executor-await-termination-time
            executor-await-termination-unit]
     :or   {executor-await-termination-time 10
            executor-await-termination-unit TimeUnit/SECONDS}}]
   (let [logger-fn     (get sentry :logger-fn)
         logger-action (format "terminating captor[%s] executor" captor-id)]
     (logger-fn :info logger-action "(-.-)Zzz...")
     (try
       (.shutdown ^ExecutorService executor)
       (let [terminated? (.awaitTermination
                           ^ExecutorService executor
                           executor-await-termination-time
                           executor-await-termination-unit)]
         (if terminated?
           (logger-fn :info "success in" logger-action)
           (logger-fn :error "failed in" logger-action))
         terminated?)
       (catch Exception error
         (logger-fn :error error "failed in" logger-action))))))

(defn capture!
  [{captor-id :id
    :keys     [sentry
               application
               executor]}
   level
   error
   & messages]
  (let [event {:level       (-> level
                                (or :error)
                                name)
               :environment (-> application
                                (get :environment)
                                name)
               :message     (->> error
                                 error->message
                                 (concat messages)
                                 (clj-string/join " "))}]
    (.submit ^ExecutorService executor
             ^Runnable (fn []
                         (let [logger-fn     (get sentry :logger-fn)
                               logger-action (format "capturing event %s with captor[%s]"
                                                     event
                                                     captor-id)]
                           (try
                             (raven/capture
                               (get sentry :data-source-name)
                               (raven-interfaces/stacktrace
                                 event
                                 error
                                 [(get application :name)]))
                             (catch Exception error
                               (logger-fn :error error "error while" logger-action))))))))
