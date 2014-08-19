(ns parenjin.enjin-ref-param
  (:use clojure.core.strint)
  (:require [parenjin.application-ref :as aref])
  (:import [parenjin.application_ref ApplicationRefProtocol ApplicationFixRefProtocol ApplicationRefResolverProtocol]))

(defn app-refs
  "convert the enjin param bindings to app ref bindings by following the ApplicationRefResolver
   references"
  [enjin-params params]
  (->> params
       (map (fn [[k v]]
              (let [resolver (get enjin-params k)]
                (if-not (instance? ApplicationRefResolverProtocol resolver)
                  (throw (RuntimeException. (<< "param: <~{k}> is not an app reference (~(type resolver))"))))
                [(aref/get-ref-name resolver) v])))
       (into {})))

(defn extract-fixed-app-refs
  "extract any app/fix-refs values from the map into a {app-ref value} map"
  [m]
  (->> m
       (filter (fn [[k v]] (instance? ApplicationFixRefProtocol v)))
       (map (fn [[k v]] [(aref/fix-ref-name v) (aref/fix-ref-value v)]))
       (into {})))

(defn literal-or-resolver-values
  "convert any app/fix-refs or app/refs in params to resolvers, leave a map of literal values or IDerefs"
  [app-promise m]
  (->> m
       (map (fn [[k v]]
              (cond (instance? ApplicationFixRefProtocol v) [k (aref/ref-resolver app-promise (aref/fix-app-ref v))]
                    (instance? ApplicationRefProtocol v) [k (aref/ref-resolver app-promise v)]
                    true [k v])))
       (into {})))
