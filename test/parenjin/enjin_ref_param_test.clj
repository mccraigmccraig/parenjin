(ns parenjin.enjin-ref-param-test
  (:use midje.sweet
        parenjin.enjin-ref-param)
  (:require [parenjin.enjin-ref-param :as enjrp]
            [parenjin.application-ref :as aref]))

(fact "app-refs should convert the enjin param bindings to app ref bindings by following ApplicationRefResolver references"
  (let [ref #app/ref :app-foo
        app-promise (promise)
        resolver (aref/ref-resolver app-promise ref)
        ref2 #app/ref :app-bar
        resolver2 (aref/ref-resolver app-promise ref2)
        enjin-params {:foo resolver :bar resolver2}]

    (#'enjrp/app-refs enjin-params {:foo ..foo-value.. :bar ..bar-value..})) => {:app-foo ..foo-value.. :app-bar ..bar-value..})

(fact "extract-fixed-app-refs should extract app-refs from any ApplicationFixRef values in the map"
  (extract-fixed-app-refs {:foo #app/ref :fooref
                           :bar #app/fix-ref [:barref 100]
                           :baz #app/fix-ref [:bazref "baz"]}) =>
  {:barref 100 :bazref "baz"})

(fact "literal-or-resolver-values should extract literal values or ref-resolvers from a map"
  (literal-or-resolver-values ..app-promise.. {:foo #app/ref :fooref
                                               :bar #app/fix-ref [:barref 100]
                                               :baz "baz"}) =>
  {:foo ..fooref-resolver..
   :bar ..barref-resolver..
   :baz "baz"}
  (provided
    (aref/ref-resolver ..app-promise.. #app/ref :fooref) => ..fooref-resolver..
    (aref/ref-resolver ..app-promise.. #app/ref :barref) => ..barref-resolver..))
