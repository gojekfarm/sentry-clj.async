(ns sentry-clj.async-test
  (:require [clojure.tools.logging :as clj-logging]
            [clojure.test :as clj-test]
            [raven-clj.core :as raven]
            [raven-clj.interfaces :as raven-interfaces]
            [sentry-clj.async :as sentry])
  (:import [com.google.common.util.concurrent MoreExecutors$DirectExecutorService]
           [java.util.concurrent ThreadPoolExecutor]
           [java.util.concurrent TimeUnit]))

(def ^:private sentry
  {:data-source-name "https://__fake_key_:__fake_secret__@sentry.example.com/42"
   :logger-fn        (fn [level
                          message
                          & more]
                       (clj-logging/logp level message more))})

(def ^:private application
  {:name        "example"
   :environment "test"})

(clj-test/deftest make-captor-test
  (clj-test/testing "WHERE by default (no options),
                     GIVEN valid Sentry & Application configuration,
                     WHEN ASKED to make a new error captor,
                     IT SHOULD result in a captor with synchronous executor (DirectExecutorService)."
    (let [{captor-id :id
           :keys     [executor]} (sentry/make-captor sentry application)]
      (clj-test/is (not (nil? captor-id)))
      (clj-test/is (instance? MoreExecutors$DirectExecutorService executor))))

  (clj-test/testing "WHERE just async option is enabled,
                     GIVEN valid Sentry & Application configuration,
                     WHEN ASKED to make a new error captor,
                     IT SHOULD result in a captor with asynchronous executor (ThreadPoolExecutor)
                     WITH default executor options."
    (let [options {:async? true}
          {captor-id :id
           :keys     [executor]} (sentry/make-captor sentry application options)]
      (clj-test/is (not (nil? captor-id)))
      (clj-test/is (instance? ThreadPoolExecutor executor))
      (clj-test/is (= 10 (.getCorePoolSize executor)))
      (clj-test/is (= 10 (.getMaximumPoolSize executor)))
      (clj-test/is (= 0 (.getKeepAliveTime executor TimeUnit/MILLISECONDS)))
      (clj-test/is (= 10 (-> executor .getQueue .remainingCapacity)))
      (clj-test/is (-> (str "corvus-" captor-id "-worker-\\d+")
                       re-pattern
                       (re-matches (-> executor
                                       .getThreadFactory
                                       (.newThread #(constantly nil))
                                       .getName))))))

  (clj-test/testing "WHERE async option is enabled WITH more async-executor options,
                     GIVEN valid Sentry & Application configuration,
                     WHEN ASKED to make a new error captor,
                     IT SHOULD result in a captor with asynchronous executor (ThreadPoolExecutor)
                     WITH default executor options."
    (let [workers-name-format-prefix "super-saiyan-"
          options                    {:async?                                 true
                                      :async-executor-workers-idle-count      42
                                      :async-executor-workers-maximum-count   69
                                      :async-executor-workers-keep-alive-time 153
                                      :async-executor-workers-keep-alive-unit TimeUnit/SECONDS
                                      :async-executor-workers-queue-size      9001
                                      :async-executor-workers-name-format     (str workers-name-format-prefix "%d")}
          {captor-id :id
           :keys     [executor]} (sentry/make-captor sentry application options)]
      (clj-test/is (not (nil? captor-id)))
      (clj-test/is (instance? ThreadPoolExecutor executor))
      (clj-test/is (= (get options :async-executor-workers-idle-count)
                      (.getCorePoolSize executor)))
      (clj-test/is (= (get options :async-executor-workers-maximum-count)
                      (.getMaximumPoolSize executor)))
      (clj-test/is (= (get options :async-executor-workers-keep-alive-time)
                      (.getKeepAliveTime executor TimeUnit/SECONDS)))
      (clj-test/is (= (get options :async-executor-workers-queue-size)
                      (-> executor .getQueue .remainingCapacity)))
      (clj-test/is (-> workers-name-format-prefix
                       (str "\\d+")
                       re-pattern
                       (re-matches (-> executor
                                       .getThreadFactory
                                       (.newThread #(constantly nil))
                                       .getName)))))))

(clj-test/deftest capture!-test
  (clj-test/testing "GIVEN valid captor,
                     WHEN ASKED to make capture an error,
                     IT SHOULD submit a task to publish error on executor."
    (let [captor  (sentry/make-captor sentry application)
          level   :warn
          error   (Throwable.)
          message "error!"]
      (let [raven:capture-arguments (atom nil)]
        (with-redefs [raven-interfaces/stacktrace (fn [event
                                                       error
                                                       application-name]
                                                    {:event            event
                                                     :error            error
                                                     :application-name application-name})
                      raven/capture               (fn [data-source-name
                                                       interfaces-stacktrace]
                                                    (reset!
                                                      raven:capture-arguments
                                                      {:data-source-name data-source-name
                                                       :stacktrace       interfaces-stacktrace}))]
          (sentry/capture! captor level error message)
          (clj-test/is (= @raven:capture-arguments
                          {:data-source-name (get sentry :data-source-name)
                           :stacktrace       {:event            {:level       (name level)
                                                                 :environment (get application :environment)
                                                                 :message     message}
                                              :error            error
                                              :application-name [(get application :name)]}})))))))
