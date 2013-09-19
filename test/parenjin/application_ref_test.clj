(ns parenjin.application-ref-test
  (:use midje.sweet
        parenjin.application-ref)
  (:require [parenjin.application-ref :as aref]))

(fact "custom reader tags should read application refs"
  #app/ref :foo => (->application-ref :foo))

(with-state-changes [(around :facts (with-bindings {#'aref/*application-refs* {..app.. {:foo ..fooval..}}}
                                     ?form))]
  (fact "resolve-ref should resolve a ref from *application-refs*"
    (#'aref/resolve-ref ..app.. :foo) => ..fooval..))

(fact "ref-resolver should create an ApplicationRefResolver"
  (let [param-ref #app/ref :foo
        app-promise (promise)
        pr (ref-resolver app-promise param-ref)]
    (deliver app-promise ..app..)
    (with-app-refs* ..app.. {:foo ..value..} (fn [] @pr)) => ..value..
    (with-app-refs* ..app.. {:foo ..another..} (fn [] @pr)) => ..another..))
