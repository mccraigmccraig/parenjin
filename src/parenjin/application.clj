(ns parenjin.application
  (:use midje.open-protocols
        potemkin)
  (:require [compojure.core :as compojure]
            [parenjin.util :as util]
            [parenjin.enjin :as enj]
            [parenjin.enjin-model :as dsm]
            [parenjin.application-proxy :as aproxy]
            [parenjin.application-ref :as aref])
  (:import [parenjin.application_ref ApplicationRef ApplicationFixRef ApplicationRefResolver]
           [parenjin.application_proxy ApplicationProxy]))

(import-vars [parenjin.application-proxy create destroy create-webservice])

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

(defn- fixup-enjins
  "replace enjin ids with delays, dealing with ApplicationRef and ApplicationFixRef cases"
  [enjin-delay-registry-promise enjins]
  (->> enjins
       (map (fn [[dep-id enjin-id]]
              (cond (instance? ApplicationRef enjin-id)
                    [dep-id enjin-id]

                    (instance?  ApplicationFixRef enjin-id) ;; the fixed value becomes the delay to the enjin
                    [dep-id (aref/map->application-fix-ref
                             {:fix-ref-name* (:fix-ref-name* enjin-id)
                              :fix-ref-value* (@enjin-delay-registry-promise (:fix-ref-value* enjin-id))})]

                    true
                    [dep-id (@enjin-delay-registry-promise enjin-id)])))
       (into {})))

(defn- create-enjin
  "create an enjin from an application's specification"
  [connector-registry app-promise enjin-delay-registry-promise enjin-spec]

  (let [model (-> enjin-spec :model util/resolve-obj)
        connectors (->> enjin-spec :connectors (map (fn [[conn-id reg-id]] [conn-id (get connector-registry reg-id)])) (into {}))
        use-params (or (:params enjin-spec) {})
]
    (enj/create-enjin model
                      :application-promise app-promise
                      :params use-params
                      :connectors connectors
                      :enjins (fixup-enjins enjin-delay-registry-promise (:enjins enjin-spec)))))

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

(defn- create-application*
  "create an application given an application specification"
  [connectors app-spec]
  (let [app-promise (promise)
        enjins (create-enjins connectors app-promise app-spec)
        application (map->application {:app-spec* app-spec
                                       :enjins* enjins})]
    (deliver app-promise application)

    application))

(defn- create-webservice*
  "create a compojure route for the application.
   - devmode : if true, then recreate the app and route on every request, if false
               just create a route"
  [app devmode]
  (if devmode
    (fn [request]
      (destroy app)
      (apply compojure/routing request (create-web-routes app)))
    (apply compojure/routes (create-web-routes app))))

(defrecord-openly application-proxy [connectors* app-spec* app*]
  Application

  (app-spec [this] app-spec*)

  (enjins [this] (enjins (create this)))
  (enjin [this id] (enjin (create this) id))

  (create-web-routes [this] (create-web-routes (create this)))

  ApplicationProxy

  (create [this] (swap! app*
                        (fn [old-app] (if-not old-app
                                       (create-application* connectors* app-spec*)
                                       old-app))))
  (destroy [this] (swap! app* (fn [_])) this)

  (create-webservice [this] (create-webservice this false))
  (create-webservice [this devmode] (create-webservice* this devmode))
  )

(defn create-application
  "create an ApplicationProxy which can create and destroy the same application
   repeatedly from the specification"
  [connectors app-spec]
  (map->application-proxy {:connectors* connectors
                           :app-spec* app-spec
                           :app* (atom nil)}))
