(ns parenjin.enjin-test
  (:use midje.sweet
        parenjin.enjin)
  (:require [parenjin.enjin :as enj]
            [parenjin.enjin-model :as enjm]
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
              :webservices ..webservices..}]

    (apply #'enj/check-requirements (apply concat args)) => true

    (provided
      (util/check-map @#'enj/check-requirements-arg-specs args) => true
      (util/check-map ..param-reqs.. ..params.. :skip-ideref? true) => true
      (util/check-map ..connector-reqs.. ..connectors.. :skip-ideref? false) => true)))

(with-state-changes [(around :facts (let [trackref (ref {})
                                             result (promise)]
                                         ?form ))]
  (fact "start-or-noop should start a never started task"
    (#'enj/start-or-noop ..this.. trackref {:foo (fn [this] this => ..this.. (deliver result true) (Thread/sleep 100))} :foo) => :running
    @result => true))

(with-state-changes [(around :facts (let [trackref (ref {:foo (future (Thread/sleep 100) ..result..)})
                                             result (promise)]
                                         ?form ))]
  (fact "start-or-noop should do nothing to a running task"
    (#'enj/start-or-noop ..this.. trackref {:foo (fn [this] this => ..this.. (deliver result true) (Thread/sleep 100))} :foo) => :running
    (deref result 100 ..nocall..) => ..nocall..))

(with-state-changes [(around :facts (let [b (promise)
                                             trackref (ref {:foo (future (deliver b true) ..before-result..)})
                                             a (promise)]
                                         ?form ))]
  (fact "start-or-noop should start a stopped task"
    @b => true
    (#'enj/start-or-noop ..this.. trackref {:foo (fn [this] this => ..this.. (deliver a ..running..) (Thread/sleep 100) ..after-result..)} :foo) => :running
    @a => ..running..
    (-> trackref deref :foo deref) => ..after-result..))

(with-state-changes [(around :facts (let [trackref (ref {})]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a never started task"
    (#'enj/stop-or-noop ..this.. trackref :foo) => :none
    (-> trackref deref :foo) => nil))

(with-state-changes [(around :facts (let [trackref (ref {:foo (future (Thread/sleep 100) ..result..)})]
                                         ?form ))]
  (fact "stop-or-noop should stop a running task"
    (#'enj/stop-or-noop ..this.. trackref :foo) => :stopped
    (-> trackref deref :foo deref) => (throws CancellationException)))

(with-state-changes [(around :facts (let [trackref (ref {:foo (future ..result..)})]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a stopped task"
    (-> trackref deref :foo deref) => ..result..
    (#'enj/stop-or-noop ..this.. trackref :foo) => :stopped))

(fact "choose-ids should choose ids"
  (#'enj/choose-ids :all ..all-ids..) => ..all-ids..
  (#'enj/choose-ids :none ..all-ids..) => []
  (set (#'enj/choose-ids [..a.. ..b.. ..c.. ..d..] [..e.. ..d.. ..b.. ..d..])) => #{..b.. ..d..})

(defn nofn [this id])
(fact "map-over-ids should map a protocol method over some ids"
  (#'enj/map-over-ids ..this.. nofn :all [..id-a.. ..id-b..]) => [..a.. ..b..]
  (provided
    (nofn ..this.. ..id-a..) => ..a..
    (nofn ..this.. ..id-b..) => ..b..))

(fact "create-simple-enjin* should create a enjin with just a model"
  (#'enj/create-simple-enjin*) => (throws RuntimeException)
  (model (#'enj/create-simple-enjin* :model {:model-type ..type..})) => {:model-type ..type..})

;; otherwise compiler borks, 'cos metaconstants are only auto-declared inside midje macros
(declare ..model.. ..params.. ..connectors.. ..enjin-deps.. ..jobs.. ..services.. ..webservices..)

(defn csd
  [& [overrides]]
  (#'enj/create-simple-enjin* :model (or (:model overrides) ..model..)
                              :params (or (:params overrides) ..params..)
                              :connectors (or (:connectors overrides) ..connectors..)
                              :enjin-deps (or (:enjin-deps overrides) ..enjin-deps..)
                              :webservices (or (:webservices overrides) ..webservices..)))

(fact
  (model (csd)) => ..model..
  (params (csd)) => ..params..
  (connectors (csd)) => ..connectors..
  (enjin-deps (csd)) => ..enjin-deps..
  (webservices (csd)) => ..webservices..
  (provided
    (#'enj/check-requirements :model ..model..
                             :params ..params..
                             :connectors ..connectors..
                             :enjin-deps ..enjin-deps..
                             :webservices ..webservices..) => true))

(fact "enjin with an IDeref parameter should check parameter type when param method retrieves param"
  (let [bar-param (atom nil)
        e (#'enj/create-simple-enjin* :model {:param-reqs {:bar String}}
                                      :params {:bar bar-param})]

    (param e :bar) => (throws RuntimeException)

    (swap! bar-param (fn [v] "boo!"))
    (param e :bar) => "boo!"))

(defn fsd
  [& [overrides]]
  (map->simple-enjin {:model* (or (:model overrides) ..model..)
                        :params* (or (:params overrides) ..params..)
                        :connectors* (or (:connectors overrides) ..connectors..)
                        :enjin-deps* (or (:enjin-deps overrides) ..enjin-deps..)
                        :webservices* (or (:webservices overrides) ..webservices..)}))

(fact "enjin-dep should dereference an IPending requirement"
  (let [ds (fsd {:enjin-deps {:foo (delay ..dep-a..)}})]
    (enjin-dep ds :foo) => ..dep-a..))

(fact "create-webservice should create some compojure routes"
  (let [ds (fsd {:webservices {:foo (fn [enjin] enjin =not=> nil ..webservice..)}})]
    (create-webservice ds :foo) => ..webservice..))

(fact "create-webservices should create some compojure routes"
  (let [ds (fsd {:webservices {:foo (fn [enjin] enjin =not=> nil ..foo-webservice..)
                               :bar (fn [enjin] enjin =not=> nil ..bar-webservice..)}})]
    (create-webservices ds [:foo :bar]) => [..foo-webservice.. ..bar-webservice..]))

(fact "create-enjin should create a enjin from the supplied requirement-resolutions"
  (let [m (enjm/create-enjin-model :foo)

        pmodel {:webservices {:ws-a ..ws-a.. :ws-b ..ws-b..}}]

    (create-enjin m
                  :params {:param-a ..param-a.. :param-b ..param-b..}
                  :connectors {:conn-a ..conn-a.. :conn-b ..conn-b..}
                  :enjin-deps {:ds-a ..ds-a.. :ds-b ..ds-b..})
    => ..ds..

    (provided
      (enjm/persist-model m) => pmodel
      (#'enj/create-simple-enjin* :model pmodel
                                  :params {:param-a ..param-a.. :param-b ..param-b..}
                                  :connectors {:conn-a ..conn-a.. :conn-b ..conn-b..}
                                  :enjin-deps {:ds-a ..ds-a.. :ds-b ..ds-b..}
                                  :webservices {:ws-a ..ws-a.. :ws-b ..ws-b..}) => ..ds..)))
