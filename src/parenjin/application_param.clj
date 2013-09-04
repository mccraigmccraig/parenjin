(ns parenjin.application-param
  (:use midje.open-protocols)
  (:import [parenjin.application_proxy ApplicationProxy]))

(defprotocol ApplicationParamRef
  (param-ref [this]))

(defrecord-openly application-param-ref [param-ref*]
  ApplicationParamRef
  (param-ref [this] param-ref*))

(defn application-param-ref-reader
  [param-ref]
  (map->application-param-ref {:param-ref* param-ref}))


(def ^:private ^:dynamic *application-params* {})

(defn with-params*
  [app params f]
  (let [use-app (if (instance? ApplicationProxy app)
                  @(:app* app)
                  app)
        new-params (assoc *application-params* use-app params)]
    (with-bindings* {#'*application-params* new-params} f)))

(defn- resolve-param
  [app ref]
  (get-in *application-params* [app ref]))

(defprotocol ApplicationParamResolver
  (set-application [this application]))

(defn param-resolver
  "create a resolver which implements IDeref and whose
   deref method resolves the parameter for the application
   app - an application
   ref - an ApplicationParamRef"
  [ref]
  (let [app-promise (promise)]
    (reify
      clojure.lang.IDeref
      (deref [this]
        (resolve-param (deref app-promise 0 nil) (param-ref ref)))

      ApplicationParamResolver
      (set-application [this application] (deliver app-promise application)))))
