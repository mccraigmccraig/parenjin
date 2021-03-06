(ns parenjin.application-ref
  (:use ;; midje.sweet
        ;; midje.open-protocols
        clojure.core.strint)
  (:require [parenjin.application-proxy :as aproxy]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [parenjin.util :as util])
  (:import [parenjin.application_proxy ApplicationProxyProtocol]))

;; app-refs refer to application variables

(defprotocol ApplicationRefProtocol
  (ref-name [this]))

(defrecord application-ref [ref-name*]
  ApplicationRefProtocol
  (ref-name [this] ref-name*))

(defn application-ref-reader
  [ref-name]
  (map->application-ref {:ref-name* ref-name}))

(def ^:private ^:dynamic *application-refs* {})

(defn- resolve-ref
  "get the value of an application variable"
  [app ref-name]
  (get-in *application-refs* [app ref-name]))

;; app-fix-refs specify the value of an application variable in some context

(defprotocol ApplicationFixRefProtocol
  (fix-ref-name [this])
  (fix-ref-value [this])
  (fix-app-ref [this]))

(defrecord application-fix-ref [fix-ref-name* fix-ref-value*]
  ApplicationFixRefProtocol
  (fix-ref-name [this] fix-ref-name*)
  (fix-ref-value [this] fix-ref-value*)
  (fix-app-ref [this] (map->application-ref {:ref-name* fix-ref-name*})))

(defn application-fix-ref-reader
  [[fix-ref-name fix-ref-value]]
  (map->application-fix-ref {:fix-ref-name* fix-ref-name :fix-ref-value* fix-ref-value}))

;; ref-resolver is an IDeref which looks up an application variable (when @/deref'ed)

(defprotocol ApplicationRefResolverProtocol
  (get-app-promise [this])
  (get-ref-name [this]))

(defn ref-resolver
  "create a resolver which implements IDeref and whose
   deref method resolves the reference for the application
   ref - an ApplicationRef"
  [app-promise ref]
  (reify
      clojure.lang.IDeref
      (deref [this]
        (let [app (clojure.core/deref app-promise 0 nil)]
          (if-not app
            (throw (RuntimeException. (<< "ref-resolver has not had application delivered: ~(ref-name ref)"))))
          (resolve-ref app (ref-name ref))))

      ApplicationRefResolverProtocol
      (get-app-promise [this] app-promise)
      (get-ref-name [this] (ref-name ref))))

(defn- unwrap-application
  [app]
  (if (instance? ApplicationProxyProtocol app)
    @(:app* app)
    app))

(defn with-app-refs*
  "set app-refs and call f. any app-refs which are derefable will be derefed first"
  [app refs f]
  (let [use-app (unwrap-application app)
        old-refs (get *application-refs* use-app)
        use-refs (->> refs (map (fn [[k v]] [k (util/deref-if-deref v)])) (into {}))
        new-refs (assoc *application-refs* use-app (util/merge-check-disjoint old-refs use-refs "can't rebind app/refs: "))]
    (with-bindings* {#'*application-refs* new-refs} f)))

(defmacro with-app-refs
  [app refs & forms]
  `(with-app-refs* ~app ~refs (fn [] ~@forms)))
