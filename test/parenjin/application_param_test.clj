(ns parenjin.application-param-test
  (:use midje.sweet
        parenjin.application-param)
  (:require [parenjin.application-param :as aparam]))

(fact "application-param-ref-reader should read application-param-refs"
  #app/param :foo => (->application-param-ref :foo))


(with-state-changes [(around :facts (with-bindings {#'aparam/*application-params* {..app.. {:foo ..fooval..}}}
                                     ?form))]
  (fact "resolve-param should resolve a param from *application-params*"
    (#'aparam/resolve-param ..app.. :foo) => ..fooval..))

(fact "param-resolver should create an ApplicationParamResolver"
  (let [ref #app/param :foo
        pr (param-resolver ref)]
    (set-application pr ..app..)
    (with-params* ..app.. {:foo ..value..} (fn [] @pr)) => ..value..
    (with-params* ..app.. {:foo ..another..} (fn [] @pr)) => ..another..))
