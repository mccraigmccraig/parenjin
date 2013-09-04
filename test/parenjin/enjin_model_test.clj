(ns parenjin.enjin-model-test
  (:use midje.sweet
        parenjin.enjin-model)
  (:require [parenjin.enjin-model :as enjm]))

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
                                          pm (persist-model m)]
                                       ?form))]
  (fact "persist-model should create a map of the model content"
    (:param-reqs pm) => {:a-param ..a-param-type.. :b-param ..b-param-type..}
    (:connector-reqs pm) => {:a-conn ..a-conn-type.. :b-conn ..b-conn-type..}
    (:enjin-reqs pm) => {:a-ds ..a-ds-type.. :b-ds ..b-ds-type..}
    (:jobs pm) => {:a-job ..a-job.. :b-job ..b-job..}
    (:services pm) => {:a-service ..a-service.. :b-service ..b-service..}
    (:webservices pm) => {:a-webservice ..a-webservice.. :b-webservice ..b-webservice..}))
