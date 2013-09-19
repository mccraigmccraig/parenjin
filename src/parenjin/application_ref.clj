(ns parenjin.application-ref
  (:use midje.open-protocols
        clojure.core.strint)
  (:require [parenjin.application-proxy :as aproxy])
  (:import [parenjin.application_proxy ApplicationProxy]))

(defprotocol ApplicationRef
  (ref-name [this]))

(defrecord-openly application-ref [ref-name*]
  ApplicationRef
  (ref-name [this] ref-name*))

(defn application-ref-reader
  [ref-name]
  (map->application-ref {:ref-name* ref-name}))

(def ^:private ^:dynamic *application-refs* {})

(defn- resolve-ref
  [app ref-name]
  (get-in *application-refs* [app ref-name]))

(defprotocol ApplicationRefResolver
  (get-ref-name [this]))

(defn- unwrap-application
  [app]
  (if (instance? ApplicationProxy app)
    @(:app* app)
    app))

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

      ApplicationRefResolver
      (get-ref-name [this] (ref-name ref))))

(defn with-app-refs*
  [app refs f]
  (let [use-app (unwrap-application app)
        new-refs (assoc *application-refs* use-app refs)]
    (with-bindings* {#'*application-refs* new-refs} f)))
