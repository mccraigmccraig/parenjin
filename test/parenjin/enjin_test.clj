(ns parenjin.enjin-test
  (:use midje.sweet
        parenjin.enjin)
  (:require [parenjin.enjin :as ds]
            [parenjin.util :as util]
            [compojure.core :as compojure])
  (:import [clojure.lang ExceptionInfo]
           [java.util.concurrent CancellationException]))

(fact "check-requirements should check all requirements against provisions"
  (let [args {:model {:param-reqs ..param-reqs..
                      :connector-reqs ..connector-reqs..}
              :params ..params..
              :connectors ..connectors..
              :enjin-deps ..enjin-deps..
              :jobs ..jobs..
              :services ..services..
              :webservices ..webservices..}]

    (apply #'ds/check-requirements (apply concat args)) => true

    (provided
      (util/check-map @#'ds/check-requirements-arg-specs args) => true
      (util/check-map ..param-reqs.. ..params..) => true
      (util/check-map ..connector-reqs.. ..connectors..) => true)))

(fact "future-status should return a status for a future"
  (#'ds/future-status nil) => :none

  (let [f (future 1)]
    (Thread/sleep 100)
    (#'ds/future-status f) => :stopped)

  (let [f (future (do (Thread/sleep 100) 1))]
    (#'ds/future-status f) => :running))

(with-state-changes [(around :facts (let [trackref (ref {})
                                             result (promise)]
                                         ?form ))]
  (fact "start-or-noop should start a never started task"
    (#'ds/start-or-noop ..this.. trackref {:foo (fn [this] this => ..this.. (deliver result true) (Thread/sleep 100))} :foo) => :running
    @result => true))

(with-state-changes [(around :facts (let [trackref (ref {:foo (future (Thread/sleep 100) ..result..)})
                                             result (promise)]
                                         ?form ))]
  (fact "start-or-noop should do nothing to a running task"
    (#'ds/start-or-noop ..this.. trackref {:foo (fn [this] this => ..this.. (deliver result true) (Thread/sleep 100))} :foo) => :running
    (deref result 100 ..nocall..) => ..nocall..))

(with-state-changes [(around :facts (let [b (promise)
                                             trackref (ref {:foo (future (deliver b true) ..before-result..)})
                                             a (promise)]
                                         ?form ))]
  (fact "start-or-noop should start a stopped task"
    @b => true
    (#'ds/start-or-noop ..this.. trackref {:foo (fn [this] this => ..this.. (deliver a ..running..) (Thread/sleep 100) ..after-result..)} :foo) => :running
    @a => ..running..
    (-> trackref deref :foo deref) => ..after-result..))

(with-state-changes [(around :facts (let [trackref (ref {})]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a never started task"
    (#'ds/stop-or-noop ..this.. trackref :foo) => :none
    (-> trackref deref :foo) => nil))

(with-state-changes [(around :facts (let [trackref (ref {:foo (future (Thread/sleep 100) ..result..)})]
                                         ?form ))]
  (fact "stop-or-noop should stop a running task"
    (#'ds/stop-or-noop ..this.. trackref :foo) => :stopped
    (-> trackref deref :foo deref) => (throws CancellationException)))

(with-state-changes [(around :facts (let [trackref (ref {:foo (future ..result..)})]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a stopped task"
    (-> trackref deref :foo deref) => ..result..
    (#'ds/stop-or-noop ..this.. trackref :foo) => :stopped))

(fact "choose-ids should choose ids"
  (#'ds/choose-ids :all ..all-ids..) => ..all-ids..
  (#'ds/choose-ids :none ..all-ids..) => []
  (set (#'ds/choose-ids [..a.. ..b.. ..c.. ..d..] [..e.. ..d.. ..b.. ..d..])) => #{..b.. ..d..})

(defn nofn [this id])
(fact "map-over-ids should map a protocol method over some ids"
  (#'ds/map-over-ids ..this.. nofn :all [..id-a.. ..id-b..]) => [..a.. ..b..]
  (provided
    (nofn ..this.. ..id-a..) => ..a..
    (nofn ..this.. ..id-b..) => ..b..))

(fact "deref-if-pending should deref an object if it is pending"
  (#'ds/deref-if-pending (delay ..val..)) => ..val..
  (#'ds/deref-if-pending ..val..) => ..val..)

(fact "create-simple-enjin* should create a enjin with just a model"
  (#'ds/create-simple-enjin*) => (throws RuntimeException)
  (model  (#'ds/create-simple-enjin* :model ..model..)) => ..model..)

(defn csd
  [& [overrides]]
  (#'ds/create-simple-enjin* :model (or (:model overrides) ..model..)
                               :params (or (:params overrides) ..params..)
                               :connectors (or (:connectors overrides) ..connectors..)
                               :enjin-deps (or (:enjin-deps overrides) ..enjin-deps..)
                               :jobs (or (:jobs overrides) ..jobs..)
                               :services (or (:services overrides) ..services..)
                               :webservices (or (:webservices overrides) ..webservices..)))

(fact
  (model (csd)) => ..model..
  (params (csd)) => ..params..
  (connectors (csd)) => ..connectors..
  (enjin-deps (csd)) => ..enjin-deps..
  (jobs (csd)) => ..jobs..
  (services (csd)) => ..services..
  (webservices (csd)) => ..webservices..
  (provided
    (#'ds/check-requirements :model ..model..
                             :params ..params..
                             :connectors ..connectors..
                             :enjin-deps ..enjin-deps..
                             :jobs ..jobs..
                             :services ..services..
                             :webservices ..webservices..) => true))

(defn fsd
  [& [overrides]]
  (map->simple-enjin {:model* (or (:model overrides) ..model..)
                        :params* (or (:params overrides) ..params..)
                        :connectors* (or (:connectors overrides) ..connectors..)
                        :enjin-deps* (or (:enjin-deps overrides) ..enjin-deps..)
                        :jobs* (or (:jobs overrides) ..jobs..)
                        :services* (or (:services overrides) ..services..)
                        :webservices* (or (:webservices overrides) ..webservices..)
                        :running-jobs* (ref (or (:running-jobs overrides) {}))
                        :running-services* (ref (or (:running-services overrides) {}))}))

(fact "enjin-dep should dereference an IPending requirement"
  (let [ds (fsd {:enjin-deps {:foo (delay ..dep-a..)}})]
    (enjin-dep ds :foo) => ..dep-a..))

(fact "start-job should start a job"
  (let [ds (fsd)
        rj (:running-jobs* ds)]
    (start-job ds :foo) => ..started..
    (provided
      (#'ds/start-or-noop ds rj ..jobs.. :foo) => ..started..)))

(fact "start-jobs should start some jobs"
  (let [ds (fsd {:jobs {:foo ..foo-job.. :bar ..bar-job.. :baz ..baz-job..}})]
    (start-jobs ds [:foo :bar]) => ..started..
    (provided
      (#'ds/map-over-ids ds start-job [:foo :bar] [:foo :bar :baz]) => ..started..)))

(fact "job-status should return the status of a job"
  (let [ds (fsd {:running-jobs {:foo ..foo-future..}})]
    (job-status ds :foo) => ..foo-status..
    (provided
      (#'ds/future-status ..foo-future..) => ..foo-status..)))

(fact "stop-job should stop a job"
  (let [ds (fsd)
        rj (:running-jobs* ds)]
    (stop-job ds :foo) => ..stopped..
    (provided
      (#'ds/stop-or-noop ds rj :foo) => ..stopped..)))

(fact "stop-jobs should stop some jobs"
  (let [ds (fsd {:jobs {:foo ..foo-job.. :bar ..bar-job.. :baz ..baz-job..}})]
    (stop-jobs ds [:foo :bar]) => ..stopped..
    (provided
      (#'ds/map-over-ids ds stop-job [:foo :bar] [:foo :bar :baz]) => ..stopped..)))

(fact "start-service should start a service"
  (let [ds (fsd)
        rj (:running-services* ds)]
    (start-service ds :foo) => ..started..
    (provided
      (#'ds/start-or-noop ds rj ..services.. :foo) => ..started..)))

(fact "start-services should start some services"
  (let [ds (fsd {:services {:foo ..foo-job.. :bar ..bar-job.. :baz ..baz-job..}})]
    (start-services ds [:foo :bar]) => ..started..
    (provided
      (#'ds/map-over-ids ds start-service [:foo :bar] [:foo :bar :baz]) => ..started..)))

(fact "service-status should return the status of a service"
  (let [ds (fsd {:running-services {:foo ..foo-future..}})]
    (service-status ds :foo) => ..foo-status..
    (provided
      (#'ds/future-status ..foo-future..) => ..foo-status..)))

(fact "stop-service should stop a service"
  (let [ds (fsd)
        rs (:running-services* ds)]
    (stop-service ds :foo) => ..stopped..
    (provided
      (#'ds/stop-or-noop ds rs :foo) => ..stopped..)))

(fact "stop-services should stop some services"
  (let [ds (fsd {:services {:foo ..foo-job.. :bar ..bar-job.. :baz ..baz-job..}})]
    (stop-services ds [:foo :bar]) => ..stopped..
    (provided
      (#'ds/map-over-ids ds stop-service [:foo :bar] [:foo :bar :baz]) => ..stopped..)))

(fact "create-webservice should create some compojure routes"
  (let [ds (fsd {:webservices {:foo (fn [enjin] enjin =not=> nil ..webservice..)}})]
    (create-webservice ds :foo) => ..webservice..))

(fact "create-webservices should create some compojure routes"
  (let [ds (fsd {:webservices {:foo (fn [enjin] enjin =not=> nil ..foo-webservice..)
                               :bar (fn [enjin] enjin =not=> nil ..bar-webservice..)}})]
    (create-webservices ds [:foo :bar]) => [..foo-webservice.. ..bar-webservice..]))
