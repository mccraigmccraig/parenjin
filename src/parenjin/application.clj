(ns parenjin.application
  (:use midje.open-protocols)
  (:require [clomponents.control :as clomp]
            [compojure.core :as compojure]
            [parenjin.util :as util]
            [parenjin.enjin :as enj]
            [parenjin.enjin-model :as dsm]))

(defprotocol Application

  "applications combine :
    - connectors
    - enjins, created from enjin-models by supplying enjin-model requirements
    - a specification, which supplies enjin-model requirements and dependencies, and defines
      which of a enjins services are to be started/stopped with the enjin

   they can be stopped and started, which stops and starts specified services of the enjins"

  (app-spec [this])

  (web-connector [this])
  (create-web-routes [this])

  (enjins [this])
  (enjin [this id])

  (start* [this proxy])
  (stop [this]))

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

  (web-connector [this] web-connector*)
  (create-web-routes [this] (create-web-routes* app-spec* enjins*))

  (enjins [this] enjins*)
  (enjin [this id] (enjins* id))

  (start* [this proxy]
    (->> enjins*
         (map (fn [[id enjin]]
                (enj/start-services enjin (get-in app-spec* [:enjins id :services]))))
         dorun)
    (clomp/destroy (web-connector this))
    (clomp/create (web-connector this) (merge (app-spec* :web) {:app (or proxy this)}))
    true)

  (stop [this]
    (->> enjins*
         (map (fn [[id enjin]]
                (enj/stop-services enjin :all)
                (enj/stop-jobs enjin :all)))
         dorun)
    (clomp/destroy (web-connector this))
    true))

(defn- create-enjin
  "create a enjin from an application's specification"
  [connector-registry enjin-delay-registry-promise enjin-spec]

  (let [model (-> enjin-spec :model util/resolve-obj)
        connectors (->> enjin-spec :connectors (map (fn [[conn-id reg-id]] [conn-id (connector-registry reg-id)])) (into {}))
        dependencies (->> enjin-spec :enjin-deps (map (fn [[dep-id ds-id]] [dep-id (@enjin-delay-registry-promise ds-id)])) (into {}))]
    (enj/create-enjin model
                      :params (:params enjin-spec)
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

(defn- create-application*
  "create an application given an application specification"
  [app-spec]
  (let [connector-registry (util/resolve-obj (:connectors app-spec))
        enjins (create-enjins connector-registry app-spec)
        web-connector (connector-registry (get-in app-spec [:web :connector]))]
    (map->application {:app-spec* app-spec
                       :web-connector* web-connector
                       :enjins* enjins})))

(defprotocol ApplicationProxy
  (create [this])
  (start [this])
  (destroy [this]))

(defrecord-openly application-proxy [app-spec* app*]
  Application

  (app-spec [this] app-spec*)

  (web-connector [this] (create this) (web-connector @app*))
  (create-web-routes [this] (create this) (create-web-routes @app*))

  (enjins [this] (create this) (enjins @app*))
  (enjin [this id] (create this) (enjin @app* id))

  (start* [this _] (create this) (start* @app* this))
  (stop [this] (create this) (stop @app*))

  ApplicationProxy

  (create [this] (if-not @app* (swap! app* (fn [_] (create-application* app-spec*)))) this)
  (start [this] (create this) (start* @app* this) this)
  (destroy [this] (when @app* (stop this) (swap! app* (fn [_]))) this))

(defn create-application
  "create an ApplicationProxy which can create and destroy the same application
   repeatedly from the specification"
  [app-spec]
  (map->application-proxy {:app-spec* app-spec
                           :app* (atom nil)})
  )
