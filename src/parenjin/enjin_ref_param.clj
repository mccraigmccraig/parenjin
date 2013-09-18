(ns parenjin.enjin-ref-param
  (:use clojure.core.strint
        midje.open-protocols)
  (:require [parenjin.application-ref :as aref])
  (:import [parenjin.application_ref ApplicationRefResolver]))

(defn- app-refs
  "convert the enjin param bindings to app ref bindings by following the ApplicationParamResolver
   references"
  [enjin params]
  (->> params
       (map (fn [[k v]]
              (let [resolver (get-in enjin [:params* k])]
                (if-not (instance? ApplicationRefResolver resolver)
                  (throw (RuntimeException. (<< "param: <~{k}> is not an app/param reference"))))
                [(aref/get-ref-name resolver) v])))
       (into {})))

(defn with-params*
  "call function f after binding reference params for the enjin"
  [enjin params f]
  (aref/with-app-refs* (deref (:application-promise* enjin)) (app-refs enjin params) f))

(defmacro with-params
  "wrap forms in a lambda after binding reference params for the enjin"
  [enjin params & forms]
  `(with-params* ~enjin ~params (fn [] ~@forms)))
