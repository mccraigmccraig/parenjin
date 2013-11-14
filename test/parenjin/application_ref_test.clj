(ns parenjin.application-ref-test
  (:use midje.sweet
        parenjin.application-ref)
  (:require [parenjin.application-ref :as aref]
            [parenjin.application :as app]))

(fact "custom reader tags should read application refs"
  #app/ref :foo => (->application-ref :foo))

(with-state-changes [(around :facts (with-bindings {#'aref/*application-refs* {..app.. {:foo ..fooval..}}}
                                     ?form))]
  (fact "resolve-ref should resolve a ref from *application-refs*"
    (#'aref/resolve-ref ..app.. :foo) => ..fooval..))


(fact "custom reader tag should read application-fix-refs"
  #app/fix-ref [:foo "100"] => (->application-fix-ref :foo "100"))

(fact "ref-resolver should create an ApplicationRefResolver"
  (let [param-ref #app/ref :foo
        app-promise (promise)
        pr (ref-resolver app-promise param-ref)]
    (deliver app-promise ..app..)
    (with-app-refs* ..app.. {:foo ..value..} (fn [] @pr)) => ..value..
    (with-app-refs* ..app.. {:foo ..another..} (fn [] @pr)) => ..another..))

(fact "with-app-refs should set app-refs and call function, unwrapping any ApplicationProxy"
  (let [param-ref #app/ref :foo
        app-promise (promise)
        pr (ref-resolver app-promise param-ref)

        _ (deliver app-promise ..app..)
        app-proxy (app/map->application-proxy {:connectors* {} :app-spec* {} :app* (atom ..app..)})]

    (with-app-refs* app-proxy {:foo ..value..} (fn [] @pr)) => ..value..
    (with-app-refs* app-proxy {:foo ..another..} (fn [] @pr)) => ..another..))

(fact "with-app-refs should set app-refs and call function, derefing any ref values which are IDerefs"
  (let [param-ref #app/ref :foo
        app-promise (promise)
        pr (ref-resolver app-promise param-ref)]
    (deliver app-promise ..app..)
    (with-app-refs* ..app.. {:foo (atom ..value..)} (fn [] @pr)) => ..value..
    (with-app-refs* ..app.. {:foo (atom ..another..)} (fn [] @pr)) => ..another..))
