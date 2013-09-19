(ns parenjin.application
  (:use midje.open-protocols
        potemkin)
  (:require [compojure.core :as compojure]
            [parenjin.util :as util]
            [parenjin.enjin :as enj]
            [parenjin.enjin-model :as dsm]
            [parenjin.application-proxy :as aproxy]
            [parenjin.application-ref :as aref])
  (:import [parenjin.application_ref ApplicationRef ApplicationRefResolver]
           [parenjin.application_proxy ApplicationProxy]))

(defprotocol Application

  "applications combine :
    - connectors
    - enjins, created from enjin-models by supplying enjin-model requirements
    - a specification, which supplies enjin-model requirements and dependencies, and defines
      which of an enjins webservices are to be created for the application"

  (app-spec [this])

  (enjins [this])
  (enjin [this id])

  (create-web-routes [this]))

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

  (create-web-routes [this] (create-web-routes* app-spec* enjins*)))

(defn- fixup-params
  "replace any params which are ApplicationRefs with resolvers for the param"
  [app-promise params]
  (->> params
       (map (fn [[k v]]
              (if (instance? ApplicationRef v)
                [k (aref/ref-resolver app-promise v)]
                [k v])))
       (into {})))

(defn- create-enjin
  "create a enjin from an application's specification"
  [connector-registry app-promise enjin-delay-registry-promise enjin-spec]

  (let [model (-> enjin-spec :model util/resolve-obj)
        connectors (->> enjin-spec :connectors (map (fn [[conn-id reg-id]] [conn-id (connector-registry reg-id)])) (into {}))
        dependencies (->> enjin-spec
                          :enjin-deps
                          (map (fn [[dep-id ds-id]] (if (instance? ApplicationRef ds-id)
                                                     [dep-id (aref/ref-resolver app-promise ds-id)]
                                                     [dep-id (@enjin-delay-registry-promise ds-id)])))
                          (into {}))]
    (enj/create-enjin model
                      :application-promise app-promise
                      :params (fixup-params app-promise (:params enjin-spec))
                      :connectors connectors
                      :enjin-deps dependencies)))

(defn- create-enjins
  [connector-registry app-promise app-spec]

  (let [enjin-delay-registry-promise (promise)]

    ;; create delays which will create an enjin when dereferenced
    (->> app-spec
         :enjins
         (map (fn [[key enjin-spec]]
                [key (delay (create-enjin connector-registry app-promise enjin-delay-registry-promise enjin-spec))]))
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
              (if (instance? ApplicationRefResolver v)
                (aref/set-application v app))))
       dorun))

(defn- create-application*
  "create an application given an application specification"
  [app-spec]
  (let [app-promise (promise)
        connector-registry (util/resolve-obj (:connectors app-spec))
        enjins (create-enjins connector-registry app-promise app-spec)
        application (map->application {:app-spec* app-spec
                                       :enjins* enjins})]
    (deliver app-promise application)

    application))

(import-vars [parenjin.application-proxy destroy create-webservice])

(defn- create-webservice*
  "create a compojure route for the application.
   - devmode : if true, then recreate the app and route on every request, if false
               just create a route"
  [app devmode]
  (if devmode
    (fn [request]
      (destroy app)
      (compojure/routing (create-web-routes app)))
    (apply compojure/routes (create-web-routes app))))

(defn- create*
  "create an app if it hasn't already been created. returns the app"
  [app-spec app-atom]
  (swap! app-atom (fn [old-app] (if-not old-app
                                 (create-application* app-spec)
                                 old-app))))

(defrecord-openly application-proxy [app-spec* app*]
  Application

  (app-spec [this] app-spec*)

  (enjins [this] (enjins (create* app-spec* app*)))
  (enjin [this id] (enjin (create* app-spec* app*) id))

  (create-web-routes [this] (create-web-routes (create* app-spec* app*)))

  ApplicationProxy

  (destroy [this] (swap! app* (fn [_])) this)

  (create-webservice [this] (create-webservice this false))
  (create-webservice [this devmode] (create-webservice* (create* app-spec* app*) devmode)))

(defn create-application
  "create an ApplicationProxy which can create and destroy the same application
   repeatedly from the specification"
  [app-spec]
  (map->application-proxy {:app-spec* app-spec
                           :app* (atom nil)}))
