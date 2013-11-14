(ns parenjin.enjin-ref-param
  (:use clojure.core.strint
        midje.open-protocols)
  (:require [parenjin.application-ref :as aref])
  (:import [parenjin.application_ref ApplicationRef ApplicationFixRef ApplicationRefResolver]))

(defn- app-refs
  "convert the enjin param bindings to app ref bindings by following the ApplicationRefResolver
   references"
  [enjin params]
  (->> params
       (map (fn [[k v]]
              (let [resolver (get-in enjin [:params* k])]
                (if-not (instance? ApplicationRefResolver resolver)
                  (throw (RuntimeException. (<< "param: <~{k}> is not an app reference (~(type resolver))"))))
                [(aref/get-ref-name resolver) v])))
       (into {})))

(defn with-params*
  "call function f after binding app references for the enjin"
  [enjin params f]
  (aref/with-app-refs* (deref (:application-promise* enjin)) (app-refs enjin params) f))

(defmacro with-params
  "wrap forms in a lambda after binding app references for the enjin"
  [enjin params & forms]
  `(with-params* ~enjin ~params (fn [] ~@forms)))

(defn extract-fixed-app-refs
  "extract any app/fix-refs values from the map into a {app-ref value} map"
  [m]
  (->> m
       (filter (fn [[k v]] (instance? ApplicationFixRef v)))
       (map (fn [[k v]] [(aref/fix-ref-name v) (aref/fix-ref-value v)]))
       (into {})))

(defn literal-or-resolver-values
  "convert any app/fix-refs or app/refs in params to resolvers, leave a map of literal values or IDerefs"
  [app-promise m]
  (->> m
       (map (fn [[k v]]
              (cond (instance? ApplicationFixRef v) [k (aref/ref-resolver app-promise (aref/fix-ref-name v))]
                    (instance? ApplicationRef v) [k (aref/ref-resolver app-promise (aref/ref-name v))]
                    true [k v])))
       (into {})))
