(ns parenjin.enjin-ref-param-test
  (:use midje.sweet
        parenjin.enjin-ref-param)
  (:require [parenjin.enjin-ref-param :as enjrp]
            [parenjin.application-ref :as aref]))

(fact "app-params should convert the enjin param bindings to app param bindings by following ApplicationParamResolver references"
  (let [ref #app/ref :app-foo
        resolver (aref/ref-resolver ref)
        ref2 #app/ref :app-bar
        resolver2 (aref/ref-resolver ref2)
        enjin {:params* {:foo resolver :bar resolver2}}]

    (#'enjrp/app-refs enjin {:foo ..foo-value.. :bar ..bar-value..})) => {:app-foo ..foo-value.. :app-bar ..bar-value..})

(fact "with-params* should set application parameters from the enjin param bindings and call the function"
  (let [ref #app/ref :app-foo
        resolver (aref/ref-resolver ref)
        enjin {:application-promise* (atom ..app..)
               :params* {:foo resolver}}]
    (fact
      (with-params* enjin {:foo ..value..} (fn [] (get-in @#'aref/*application-refs* [..app.. :app-foo]))) => ..value..)))

(fact "with-params should set application parameters from the enjin param bindings and call the forms wrapped in a lambda"
    (let [ref #app/ref :app-foo
        resolver (aref/ref-resolver ref)
        enjin {:application-promise* (atom ..app..)
               :params* {:foo resolver}}]
    (fact
      (with-params enjin {:foo ..value..} (get-in @#'aref/*application-refs* [..app.. :app-foo])) => ..value..)))
