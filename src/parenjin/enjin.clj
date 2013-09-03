(ns parenjin.enjin
  (:use midje.open-protocols
        clojure.core.strint
        clojure.core.incubator)
  (:require [clojure.set :as set]
            [compojure.core :as compojure]
            [parenjin.util :as util]))

(def ^:private check-requirements-arg-specs
  {:model Object
   :params true
   :connectors true
   :enjin-deps true
   :jobs true
   :services true
   :webservices true})

(defn- check-requirements
  "check that the requirements declared in the enjins model are met"
  [& {:keys [model params connectors enjin-deps jobs services webservices] :as args}]

  (util/check-map check-requirements-arg-specs args)

  ;; check all the requirements are met
  (->>  [[:param-reqs params]
         [:connector-reqs connectors]]
        (reduce (fn [result [req-field prov]]
                  (and result
                       (util/check-map (req-field model) prov)))
                true)))

(defn- future-status
  "get the status of a future, returning :running, :stopped or :none"
  [f]
  (cond (and f (not (realized? f))) :running
        f :stopped
        true :none))

(defn- start-or-noop
  "if it's not running call (defs id) in a future and retain the future in <trackref>"
  [this trackref defs id]
  (dosync
   (let [f ((ensure trackref) id)]
     (if (or (not f) (realized? f))
       (ref-set trackref
                (assoc @trackref id
                       (future ((defs id) this)))))))
  (future-status (@trackref id)))

(defn- stop-or-noop
  "if it is running, stop the future in (@trackref id)"
  [this trackref id]
  (dosync
   (if-let [f ((ensure trackref) id)]
     (future-cancel f)))
  (future-status (@trackref id)))

(defn- choose-ids
  "choose ids, which may be a list of ids, :all or :none"
  [ids-spec all-ids]
  (cond (= ids-spec :all) all-ids
        (= ids-spec :none) []
        true (vec (set/intersection (set ids-spec) (set all-ids)))))

(defn- map-over-ids
  "map a protocol function over some ids"
  [this f ids-spec all-ids]
  (->> (choose-ids ids-spec all-ids)
       (map (fn [id] (f this id)))
       doall))

(defn- deref-if-pending
  "dereference an object if it's an IPending, letting circular dependencies
   between enjins be expressed as Delays to other enjins"
  [obj]
  (if (instance? clojure.lang.IPending obj)
    (deref obj)
    obj))

(defprotocol Enjin
  "Enjin : a enjin with a particular schema, which has a bunch of
   long-running batch jobs which may be run for it
   a bunch of non-terminating services to be run on it and a bunch of web-services
   which may be run on it"

  (model [this]
    "the EnjinModel from which this Enjin was created")

  (params [this]
    "the params supplied at creation time")
  (param [this name]
    "get the value of a single param")
  (connectors [this]
    "the connectors supplied at creation time")
  (connector [this id]
    "get a connector by id")
  (enjin-deps [this]
    "the enjin-deps supplied at creation time")
  (enjin-dep [this id]
    "get a enjin-dep by id")

  (jobs [this]
    "the jobs defined on the Enjin")
  (start-job [this job-id]
    "start the job with id <job-id>")
  (start-jobs [this job-ids]
    "start jobs with ids <job-ids>, which may be a list of ids or :all")
  (job-status [this job-id]
    "fetch the status of job with id <job-id>")
  (stop-job [this job-id]
    "stop the job with id <job-id>")
  (stop-jobs [this job-ids]
    "stop jobs with ids <job-ids>, which may be a list of ids or :all")

  (services [this]
    "the services defined on the Enjin")
  (start-service [this service-id]
    "start the service with id <service-id>")
  (start-services [this service-ids]
    "start services with ids <service-ids> which may be a list of ids or :all")
  (service-status [this service-id]
    "fetch the status of service with id <service-id>")
  (stop-service [this service-id]
    "stop the service with id <service-id>")
  (stop-services [this service-ids]
    "stop services with ids <service-ids> which may be a list of ids or :all")

  (webservices [this]
    "the webservices defined on the Enjin")
  (create-webservice [this webservice-id]
    "create the webservice with id <webservice-id>")
  (create-webservices [this webservice-ids]
    "create webservices with ids <webservice-ids> which may be a list of ids or :all"))

(defrecord-openly simple-enjin [model* params* connectors* enjin-deps* jobs* services* webservices* running-jobs* running-services*]
  Enjin
  (model [this] model*)
  (params [this] params*)
  (param [this name] (params* name))
  (connectors [this] connectors*)
  (connector [this id] (connectors* id))
  (enjin-deps [this] enjin-deps*)
  (enjin-dep [this id]
    (let [req-type (get-in model* [:enjin-reqs id])
          ds (deref-if-pending (enjin-deps* id))
          ds-type (get-in ds [:model* :model-type])]
      (if-not (= req-type ds-type)
        (throw (RuntimeException. (<< "enjin ~{id} is of type ~{ds-type} but is required to be of type ~{req-type}"))))
      ds))

  (jobs [this] jobs*)
  (start-job [this job-id] (start-or-noop this running-jobs* jobs* job-id))
  (start-jobs [this job-ids] (map-over-ids this start-job job-ids (keys jobs*)))
  (job-status [this job-id] (future-status (@running-jobs* job-id)))
  (stop-job [this job-id] (stop-or-noop this running-jobs* job-id))
  (stop-jobs [this job-ids] (map-over-ids this stop-job job-ids (keys jobs*)))

  (services [this] services*)
  (start-service [this service-id] (start-or-noop this running-services* services* service-id))
  (start-services [this service-ids] (map-over-ids this start-service service-ids (keys services*)))
  (service-status [this service-id] (future-status (@running-services* service-id)))
  (stop-service [this service-id] (stop-or-noop this running-services* service-id))
  (stop-services [this service-ids] (map-over-ids this stop-service service-ids (keys services*)))

  (webservices [this] webservices*)
  (create-webservice [this webservice-id] ((webservices* webservice-id) this))
  (create-webservices [this webservice-ids] (map-over-ids this create-webservice webservice-ids (keys webservices*))))

(def ^:private create-simple-enjin*-arg-specs
  {:model Object
   :params true
   :connectors true
   :enjin-deps true
   :jobs true
   :services true
   :webservices true})


(defn- create-simple-enjin*
  [& {:keys [model params connectors enjin-deps jobs services webservices] :as args}]
  (if-not model
    (throw (RuntimeException. "no model")))

  (util/check-map create-simple-enjin*-arg-specs args)

  (check-requirements :model model
                      :params params
                      :connectors connectors
                      :enjin-deps enjin-deps
                      :jobs jobs
                      :services services
                      :webservices webservices)

  (map->simple-enjin {:model* model
                        :params* (or params {})
                        :connectors* (or connectors {})
                        :enjin-deps* (or enjin-deps {})
                        :jobs* (or jobs {})
                        :services* (or services {})
                        :webservices* (or webservices {})
                        :running-jobs* (ref {})
                        :running-services* (ref {})}))

;; limit the depth to which a enjin will print, avoiding
;; blowing the stack when circular references are used
(defn- print-enjin
  [enjin writer]
  (let [m (->> (keys enjin)
               (map (fn [k] [k (k enjin)]))
               (into {}))]
    (.write writer "#")
    (.write writer (.getName simple-enjin))
    (if-not *print-level*
      (binding [*print-level* 3]
        (#'clojure.core/pr-on m writer))
      (#'clojure.core/pr-on m writer))))

(defmethod print-method simple-enjin
  [enjin writer]
  (print-enjin enjin writer)
  )

(defmethod print-dup simple-enjin
  [enjin writer]
  (print-enjin enjin writer))
