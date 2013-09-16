(ns parenjin.application
  (:use midje.open-protocols
        potemkin)
  (:require [clomponents.control :as clomp]
            [compojure.core :as compojure]
            [parenjin.util :as util]
            [parenjin.enjin :as enj]
            [parenjin.enjin-model :as dsm]
            [parenjin.application-proxy :as aproxy]
            [parenjin.application-param :as aparam])
  (:import [parenjin.application_param ApplicationParamRef ApplicationParamResolver]
           [parenjin.application_proxy ApplicationProxy]))

(defprotocol Application

  "applications combine :
    - connectors
    - enjins, created from enjin-models by supplying enjin-model requirements
    - a specification, which supplies enjin-model requirements and dependencies, and defines
      which of an enjins webservices are to be created with the enjin's create-webservice"

  (app-spec [this])

  (enjins [this])
  (enjin [this id])

  (create-webservice [this]))

(defn- create-web-routes*
  "given an app-spec and a bunch of enjins, create a set of compojure routes for the webservices
   on the enjins exposed by the app-spec"
  [app-spec enjins]
  (let [enjin-specs (app-spec :enjins)]
    (->> enjin-specs
         (map (fn [[ds-id ds-spec]]
                (enj/create-webservices (enjins ds-id)
                                       (or (:webservices ds-spec) :all))))
         (apply concat))))

(defrecord-openly application [app-spec* web-connector* enjins*]
  Application

  (app-spec [this] app-spec*)

  (enjins [this] enjins*)
  (enjin [this id] (enjins* id))

  (create-webservice [this] (create-web-routes* app-spec* enjins*)))

(defn- fixup-params
  "replace any params which are ApplicationParamRefs with resolvers for the param"
  [params]
  (->> params
       (map (fn [[k v]]
              (if (instance? ApplicationParamRef v)
                [k (aparam/param-resolver v)]
                [k v])))
       (into {})))

(defn- create-enjin
  "create a enjin from an application's specification"
  [connector-registry enjin-delay-registry-promise enjin-spec]

  (let [model (-> enjin-spec :model util/resolve-obj)
        connectors (->> enjin-spec :connectors (map (fn [[conn-id reg-id]] [conn-id (connector-registry reg-id)])) (into {}))
        dependencies (->> enjin-spec :enjin-deps (map (fn [[dep-id ds-id]] [dep-id (@enjin-delay-registry-promise ds-id)])) (into {}))]
    (enj/create-enjin model
                      :params (fixup-params (:params enjin-spec))
                      :connectors connectors
                      :enjin-deps dependencies)))

(defn- create-enjins
  [connector-registry app-spec]

  (let [enjin-delay-registry-promise (promise)]

    ;; create delays which will create a enjin when dereferenced
    (->> app-spec
         :enjins
         (map (fn [[key enjin-spec]]
                [key (delay (create-enjin connector-registry enjin-delay-registry-promise enjin-spec))]))
         (into {})
         (deliver enjin-delay-registry-promise))

    ;; defeference the delays, creating all enjins
    (->> @enjin-delay-registry-promise
         (map (fn [[key enjin-delay]]
                [key (deref enjin-delay)]))
         (into {}))))

(defn- deliver-app-to-param-resolvers
  [app params]
  (->> params
       (map (fn [[k v]]
              (if (instance? ApplicationParamResolver v)
                (aparam/set-application v app))))
       dorun))

(defn- create-application*
  "create an application given an application specification"
  [app-spec]
  (let [connector-registry (util/resolve-obj (:connectors app-spec))
        enjins (create-enjins connector-registry app-spec)
        application (map->application {:app-spec* app-spec
                                       :enjins* enjins})]
    ;; deliver the application to all it's enjins, and to any ApplicationParamResolvers
    (->> enjins
         (map (fn [[id enjin]]
                (deliver (:application-promise* enjin) application)
                (deliver-app-to-param-resolvers application (:params* enjin))))
         dorun)

    application))

(import-vars [parenjin.application-proxy create destroy])

(defrecord-openly application-proxy [app-spec* app*]
  Application

  (app-spec [this] app-spec*)

  (enjins [this] (create this) (enjins @app*))
  (enjin [this id] (create this) (enjin @app* id))

  (create-webservice [this] (create this) (create-webservice @app*))

  ApplicationProxy

  (create [this] (if-not @app* (swap! app* (fn [_] (create-application* app-spec*)))) this)
  (destroy [this] (when @app* (swap! app* (fn [_]))) this))

(defn create-application
  "create an ApplicationProxy which can create and destroy the same application
   repeatedly from the specification"
  [app-spec]
  (map->application-proxy {:app-spec* app-spec
                           :app* (atom nil)}))
