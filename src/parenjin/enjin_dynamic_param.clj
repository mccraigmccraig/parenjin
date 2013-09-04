(ns parenjin.enjin-dynamic-param
  (:use clojure.core.strint
        midje.open-protocols)
  (:require [parenjin.application-param :as aparam])
  (:import [parenjin.application_param ApplicationParamResolver]))

(defn- app-params
  "convert the enjin param bindings to app param bindings by following the ApplicationParamResolver
   references"
  [enjin params]
  (->> params
       (map (fn [[k v]]
              (let [resolver (get-in enjin [:params* k])]
                (if-not (instance? ApplicationParamResolver resolver)
                  (throw (RuntimeException. (<< "param: <~{k}> is not an app/param reference"))))
                [(aparam/get-param-ref resolver) v])))
       (into {})))

(defn with-params*
  "call function f after binding reference params for the enjin"
  [enjin params f]
  (aparam/with-params* (deref (:application-promise* enjin)) (app-params enjin params) f))

(defmacro with-params
  "wrap forms in a lambda after binding reference params for the enjin"
  [enjin params & forms]
  `(with-params* ~enjin ~params (fn [] ~@forms)))
