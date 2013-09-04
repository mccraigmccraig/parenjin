(ns parenjin.application-param
  (:use midje.open-protocols))

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
  (let [new-params (assoc *application-params* app params)]
    (with-bindings* {#'*application-params* new-params} f)))

(defn- resolve-param
  [app ref]
  (get-in *application-params* [app ref]))

(defn param-resolver
  "create a resolver which implements IDeref and whose
   deref method resolves the parameter for the application
   app - an application
   ref - an ApplicationParamRef"
  [app ref]
  (reify clojure.lang.IDeref
    (deref [this]
      (resolve-param app (param-ref ref)))))
