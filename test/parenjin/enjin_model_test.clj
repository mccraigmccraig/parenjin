(ns parenjin.enjin-model-test
  (:use midje.sweet
        parenjin.enjin-model)
  (:require [parenjin.enjin :as ds]
            [parenjin.enjin-model :as dsm]))

(fact "create-enjin-model should create a EnjinModel"
  (let [m (create-enjin-model :foo)]
    (instance? parenjin.enjin_model.enjin-model m) => true
    (model-type m) => :foo))


(with-state-changes
  [(around :facts (let [m (create-enjin-model :foo)] ?form))]

  (fact "requires-param should register a parameter requirement"
    (-> (requires-param m :bar ..param-type..)
        :param-reqs*
        deref
        :bar) => ..param-type..)

  (fact "requires-connector should register a connector requirement"
    (-> (requires-connector m :bar ..conn-type..)
        :connector-reqs*
        deref
        :bar) => ..conn-type..)

  (fact "requires-enjin should register a enjin requirement"
    (-> (requires-enjin m :bar ..model-type..)
        :enjin-reqs*
        deref
        :bar) => ..model-type..)

  (fact "defjob should register a job"
    (-> (defjob m :j ..job..)
        :jobs*
        deref
        :j) => ..job..)

  (fact "defwebservice should register a webservice"
    (-> (defwebservice m :ws ..webservice..)
        :webservices*
        deref
        :ws) => ..webservice..))

(with-state-changes [(around :facts (let [m (-> (create-enjin-model :foo)
                                                 (requires-param :a-param ..a-param-type..)
                                                 (requires-param :b-param ..b-param-type..)
                                                 (requires-connector :a-conn ..a-conn-type..)
                                                 (requires-connector :b-conn ..b-conn-type..)
                                                 (requires-enjin :a-ds ..a-ds-type..)
                                                 (requires-enjin :b-ds ..b-ds-type..)
                                                 (defjob :a-job ..a-job..)
                                                 (defjob :b-job ..b-job..)
                                                 (defservice :a-service ..a-service..)
                                                 (defservice :b-service ..b-service..)
                                                 (defwebservice :a-webservice ..a-webservice..)
                                                 (defwebservice :b-webservice ..b-webservice..))
                                          pm (#'dsm/persist-model m)]
                                       ?form))]
  (fact "persist-model should create a map of the model content"
    (:param-reqs pm) => {:a-param ..a-param-type.. :b-param ..b-param-type..}
    (:connector-reqs pm) => {:a-conn ..a-conn-type.. :b-conn ..b-conn-type..}
    (:enjin-reqs pm) => {:a-ds ..a-ds-type.. :b-ds ..b-ds-type..}
    (:jobs pm) => {:a-job ..a-job.. :b-job ..b-job..}
    (:services pm) => {:a-service ..a-service.. :b-service ..b-service..}
    (:webservices pm) => {:a-webservice ..a-webservice.. :b-webservice ..b-webservice..}))

(fact "create-enjin should create a enjin from the supplied requirement-resolutions"
  (let [m (create-enjin-model :foo)]
    (requires-param m :param-a ..param-a-type..)
    (requires-param m :param-b ..param-b-type..)

    (requires-connector m :conn-a ..conn-a-type..)
    (requires-connector m :conn-b ..conn-b-type..)

    (requires-enjin m :ds-a ..model-a-type..)
    (requires-enjin m :ds-b ..model-b-type..)

    (defjob m :j-a ..job-a..)
    (defjob m :j-b ..job-b..)

    (defservice m :serv-a ..serv-a..)
    (defservice m :serv-b ..serv-b..)

    (defwebservice m :ws-a ..ws-a..)
    (defwebservice m :ws-b ..ws-b..)

    (create-enjin m
                    :params {:param-a ..param-a.. :param-b ..param-b..}
                    :connectors {:conn-a ..conn-a.. :conn-b ..conn-b..}
                    :enjin-deps {:ds-a ..ds-a.. :ds-b ..ds-b..})
    => ..ds..

    (provided
      (#'ds/create-simple-enjin* :model (#'dsm/persist-model m)
                                   :params {:param-a ..param-a.. :param-b ..param-b..}
                                   :connectors {:conn-a ..conn-a.. :conn-b ..conn-b..}
                                   :enjin-deps {:ds-a ..ds-a.. :ds-b ..ds-b..}
                                   :jobs {:j-a ..job-a.. :j-b ..job-b..}
                                   :services {:serv-a ..serv-a.. :serv-b ..serv-b..}
                                   :webservices {:ws-a ..ws-a.. :ws-b ..ws-b..}) => ..ds..)))
