(ns parenjin.enjin-test
  (:use midje.sweet
        parenjin.enjin)
  (:require [parenjin.enjin-ref-param :as enjrp]
            [parenjin.application-ref :as aref]
            [parenjin.enjin :as enj]
            [parenjin.enjin-model :as enjm]
            [parenjin.util :as util]
            [compojure.core :as compojure]))

(fact "check-requirements should check all requirements against provisions"
  (let [args {:model {:param-reqs ..param-reqs..
                      :connector-reqs ..connector-reqs..}
              :params ..params..
              :connectors ..connectors..
              :enjins ..enjins..
              :webservices ..webservices..
              :jobs ..jobs..}]

    (apply #'enj/check-requirements (apply concat args)) => true

    (provided
      (util/check-map @#'enj/check-requirements-arg-specs args) => true
      (util/check-map ..param-reqs.. ..params.. :skip-ideref? true) => true
      (util/check-map ..connector-reqs.. ..connectors.. :skip-ideref? false) => true)))

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
(declare ..model.. ..params.. ..connectors.. ..enjins.. ..jobs.. ..services.. ..webservices..)

(defn csd
  [& [overrides]]
  (#'enj/create-simple-enjin* :model (or (:model overrides) ..model..)
                              :params (or (:params overrides) ..params..)
                              :connectors (or (:connectors overrides) ..connectors..)
                              :enjins (or (:enjins overrides) ..enjins..)
                              :webservices (or (:webservices overrides) ..webservices..)
                              :jobs (or (:jobs overrides) ..jobs..)))

(fact
  (model (csd)) => ..model..
  (params (csd)) => ..params..
  (connectors (csd)) => ..connectors..
  (enjins (csd)) => ..enjins..
  (webservices (csd)) => ..webservices..
  (jobs (csd)) => ..jobs..
  (provided
    (#'enj/check-requirements :model ..model..
                             :params ..params..
                             :connectors ..connectors..
                             :enjins ..enjins..
                             :webservices ..webservices..
                             :jobs ..jobs..) => true))

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
                      :enjins* (or (:enjins overrides) ..enjins..)
                      :webservices* (or (:webservices overrides) ..webservices..)
                      :jobs* (or (:jobs overrides) ..jobs..)
                      :running-jobs* (ref (or (:running-jobs overrides) {}))}))

(fact "enjin should dereference an IPending requirement"
  (let [ds (fsd {:enjins {:foo (delay ..dep-a..)}})]
    (enjin ds :foo) => ..dep-a..))

(fact "create-webservice should create some compojure routes"
  (let [ds (fsd {:webservices {:foo (fn [enjin] enjin =not=> nil ..webservice..)}})]
    (create-webservice ds :foo) => ..webservice..))

(fact "create-webservices should create some compojure routes"
  (let [ds (fsd {:webservices {:foo (fn [enjin] enjin =not=> nil ..foo-webservice..)
                               :bar (fn [enjin] enjin =not=> nil ..bar-webservice..)}})]
    (create-webservices ds [:foo :bar]) => [..foo-webservice.. ..bar-webservice..]))

(fact "start-job should start a job"
  (let [ds (fsd)
        rj (:running-jobs* ds)]
    (start-job ds :foo) => ..started..
    (provided
      (util/start-or-noop ds rj ..jobs.. :foo) => ..started..)))

(fact "start-jobs should start some jobs"
  (let [ds (fsd {:jobs {:foo ..foo-job.. :bar ..bar-job.. :baz ..baz-job..}})]
    (start-jobs ds [:foo :bar]) => ..started..
    (provided
      (#'enj/map-over-ids ds start-job [:foo :bar] [:foo :bar :baz]) => ..started..)))

(fact "job-status should return the status of a job"
  (let [ds (fsd {:running-jobs {:foo ..foo-future..}})]
    (job-status ds :foo) => ..foo-status..
    (provided
      (util/future-status ..foo-future..) => ..foo-status..)))

(fact "stop-job should stop a job"
  (let [ds (fsd)
        rj (:running-jobs* ds)]
    (stop-job ds :foo) => ..stopped..
    (provided
      (util/stop-or-noop ds rj :foo) => ..stopped..)))

(fact "stop-jobs should stop some jobs"
  (let [ds (fsd {:jobs {:foo ..foo-job.. :bar ..bar-job.. :baz ..baz-job..}})]
    (stop-jobs ds [:foo :bar]) => ..stopped..
    (provided
      (#'enj/map-over-ids ds stop-job [:foo :bar] [:foo :bar :baz]) => ..stopped..)))

(fact "create-enjin should create a enjin from the supplied requirement-resolutions"
  (let [m (enjm/create-enjin-model :foo)

        pmodel {:webservices {:ws-a ..ws-a.. :ws-b ..ws-b..}
                :jobs {:job-a ..job-a.. :job-b ..job-b..}}]

    (create-enjin m
                  :application-promise ..promise..
                  :params {:param-a ..param-a.. :param-b ..param-b..}
                  :connectors {:conn-a ..conn-a.. :conn-b ..conn-b..}
                  :enjins {:ds-a ..ds-a.. :ds-b ..ds-b..})
    => {:enjin* ..ds.. :application-promise* ..promise.. :app-refs* {}}

    (provided
      (enjm/persist-model m) => pmodel
      (#'enj/create-simple-enjin* :model pmodel
                                  :application-promise ..promise..
                                  :params {:param-a ..param-a.. :param-b ..param-b..}
                                  :connectors {:conn-a ..conn-a.. :conn-b ..conn-b..}
                                  :enjins {:ds-a ..ds-a.. :ds-b ..ds-b..}
                                  :webservices {:ws-a ..ws-a.. :ws-b ..ws-b..}
                                  :jobs {:job-a ..job-a.. :job-b ..job-b..}) => ..ds..)))

(fact "create-enjin should store any fixed app-refs in the proxy"
  (let [m (enjm/create-enjin-model :foo)

        pmodel {:webservices {}
                :jobs {}}

        params {:param-a #app/fix-ref [:param-a-ref "100"]
                :param-b #app/ref :param-b-ref
                :param-c ..param-c..}

        enjins {:enjin-a #app/fix-ref [:enjin-a-ref ..enjin-a-delay..]
                :enjin-b #app/ref :enjin-b-ref
                :enjin-c ..enjin-c-delay..}]

    (create-enjin m
                  :application-promise ..promise..
                  :params params
                  :connectors {}
                  :enjins enjins)
    => {:enjin* ..ds.. :application-promise* ..promise.. :app-refs* {:param-a-ref "100" :enjin-a-ref ..enjin-a-delay..}}

    (provided
      (enjm/persist-model m) => pmodel

      (enjrp/literal-or-resolver-values ..promise.. params) => {:param-a ..param-a-ref-resolver..
                                                                :param-b ..param-b-ref-resolver..
                                                                :param-c ..param-c..}
      (enjrp/literal-or-resolver-values ..promise.. enjins) => {:enjin-a ..enjin-a-ref-resolver..
                                                                :enjin-b ..enjin-b-ref-resolver..
                                                                :enjin-c ..enjin-c-delay..}

      (#'enj/create-simple-enjin* :model pmodel
                                  :application-promise ..promise..
                                  :params {:param-a ..param-a-ref-resolver..
                                           :param-b ..param-b-ref-resolver..
                                           :param-c ..param-c..}
                                  :connectors {}
                                  :enjins {:enjin-a ..enjin-a-ref-resolver..
                                           :enjin-b ..enjin-b-ref-resolver..
                                           :enjin-c ..enjin-c-delay..}
                                  :webservices {}
                                  :jobs {}) => ..ds..)))

(fact "with-params* should set application refs from the enjin param bindings and call the function"
  (let [ref #app/ref :app-foo
        app-promise (promise)
        resolver (aref/ref-resolver app-promise ref)
        enjin (map->simple-enjin {:application-promise* app-promise
                                  :params* {:foo resolver}})]
    (deliver app-promise ..app..)
    (fact
      (with-params* enjin {:foo ..value..} (fn [] (get-in @#'aref/*application-refs* [..app.. :app-foo]))) => ..value..)))

(fact "with-params should set application refs from the enjin param bindings and call the forms wrapped in a lambda"
  (let [ref #app/ref :app-foo
        app-promise (promise)
        resolver (aref/ref-resolver app-promise ref)
        enjin (map->simple-enjin {:application-promise* (atom ..app..)
                                  :params* {:foo resolver}})]
    (deliver app-promise ..app..)
    (fact
      (with-params enjin {:foo ..value..} (get-in @#'aref/*application-refs* [..app.. :app-foo])) => ..value..)))
