(ns parenjin.enjin-ref-param-test
  (:use midje.sweet
        parenjin.enjin-ref-param)
  (:require [parenjin.enjin-ref-param :as enjrp]
            [parenjin.application-param :as aparam]))

(fact "app-params should convert the enjin param bindings to app param bindings by following ApplicationParamResolver references"
  (let [ref #app/param :app-foo
        resolver (aparam/param-resolver ref)
        ref2 #app/param :app-bar
        resolver2 (aparam/param-resolver ref2)
        enjin {:params* {:foo resolver :bar resolver2}}]

    (#'enjrp/app-params enjin {:foo ..foo-value.. :bar ..bar-value..})) => {:app-foo ..foo-value.. :app-bar ..bar-value..})

(fact "with-params* should set application parameters from the enjin param bindings and call the function"
  (let [ref #app/param :app-foo
        resolver (aparam/param-resolver ref)
        enjin {:application-promise* (atom ..app..)
               :params* {:foo resolver}}]
    (fact
      (with-params* enjin {:foo ..value..} (fn [] (get-in @#'aparam/*application-params* [..app.. :app-foo]))) => ..value..)))

(fact "with-params should set application parameters from the enjin param bindings and call the forms wrapped in a lambda"
    (let [ref #app/param :app-foo
        resolver (aparam/param-resolver ref)
        enjin {:application-promise* (atom ..app..)
               :params* {:foo resolver}}]
    (fact
      (with-params enjin {:foo ..value..} (get-in @#'aparam/*application-params* [..app.. :app-foo])) => ..value..)))
