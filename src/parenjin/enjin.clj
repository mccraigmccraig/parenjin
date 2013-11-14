(ns parenjin.enjin
  (:use midje.open-protocols
        clojure.core.strint
        clojure.core.incubator
        potemkin)
  (:require [clojure.set :as set]
            [compojure.core :as compojure]
            [parenjin.util :as util]
            [parenjin.enjin-model :as enjm]
            parenjin.enjin-ref-param)
  (:import [parenjin.enjin_model EnjinModel]))

(import-vars [parenjin.enjin-ref-param with-params* with-params])

(def ^:private check-requirements-arg-specs
  {:model true
   :params true
   :connectors true
   :enjins true
   :webservices true
   :jobs true})

(defn- check-requirements
  "check that the requirements declared in the enjins model are met"
  [& {:keys [model params connectors enjins webservices jobs] :as args}]

  (util/check-map check-requirements-arg-specs args)

  ;; check all the requirements are met
  (->>  [[:param-reqs params true]
         [:connector-reqs connectors false]]
        (reduce (fn [result [req-field prov skip-ideref?]]
                  (and result
                       (util/check-map (req-field model) prov :skip-ideref? skip-ideref?)))
                true)))

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

(defprotocol Enjin
  "Enjin : an enjin with a particular schema, which has a bunch of connectors, a bunch
   of enjin dependcies, and a bunch of webservices"

  (model [this]
    "the EnjinModel from which this Enjin was created")

  (application [this]
    "the application this Enjin belongs to")

  (params [this]
    "the params supplied at creation time")
  (param [this name]
    "get the value of a single param")
  (connectors [this]
    "the connectors supplied at creation time")
  (connector [this id]
    "get a connector by id")
  (enjins [this]
    "the enjins supplied at creation time")
  (enjin [this id]
    "get a enjin by id")

  (webservices [this]
    "the webservices defined on the Enjin")
  (create-webservice [this webservice-id]
    "create the webservice with id <webservice-id>")
  (create-webservices [this webservice-ids]
    "create webservices with ids <webservice-ids> which may be a list of ids or :all")

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
    "stop jobs with ids <job-ids>, which may be a list of ids or :all"))

(def ^:private ^:dynamic *current-enjin* nil)

(defn current-enjin
  "get the current enjin"
  []
  *current-enjin*)

(defn with-enjin*
  "bind *current-enjin* and call function f"
  [enjin f]
  (with-bindings {#'*current-enjin* enjin} f))

(defmacro with-enjin
  "wrap forms in a lambda, and call with current enjin bound"
  [enjin & forms] `(with-enjin* ~enjin (fn [] ~@forms)))

(defn- wrap-current-enjin
  "wrap a compojure handler in a with-enjin form so that
   the enjin is available during compojure request handling"
  [handler enjin]
  (fn [& params]
    (with-enjin enjin
      (apply handler params))))

(defrecord-openly simple-enjin [model* application-promise* params* connectors* enjins* webservices* jobs* running-jobs*]
  Enjin
  (model [this] model*)

  (application [this] (if (and application-promise* (realized? application-promise*))
                        @application-promise*))

  (params [this] params*)
  (param [this name] (let [p (params* name)]
                       (if (util/derefable? p)
                         (if (util/check-val name (get-in model* [:param-reqs name]) @p)
                           @p)
                         p)))
  (connectors [this] connectors*)
  (connector [this id] (connectors* id))
  (enjins [this] enjins*)
  (enjin [this id]
    (let [req-type (get-in model* [:enjin-reqs id])
          ds (util/deref-if-deref (enjins* id))
          ds-type (get-in ds [:model* :model-type])]
      (if-not (= req-type ds-type)
        (throw (RuntimeException. (<< "enjin <~{id}> is of type <~{ds-type}> but is required to be of type <~{req-type}>"))))
      ds))

  (webservices [this] webservices*)
  (create-webservice [this webservice-id] ((webservices* webservice-id) this))
  (create-webservices [this webservice-ids] (map-over-ids this create-webservice webservice-ids (keys webservices*)))

  (jobs [this] jobs*)
  (start-job [this job-id] (util/start-or-noop this running-jobs* jobs* job-id))
  (start-jobs [this job-ids] (map-over-ids this start-job job-ids (keys jobs*)))
  (job-status [this job-id] (util/future-status (@running-jobs* job-id)))
  (stop-job [this job-id] (util/stop-or-noop this running-jobs* job-id))
  (stop-jobs [this job-ids] (map-over-ids this stop-job job-ids (keys jobs*))))

(defn- create-simple-enjin*
  [& {:keys [model application-promise params connectors enjins webservices jobs]}]
  (if-not model
    (throw (RuntimeException. "model required")))

  (check-requirements :model model
                      :params params
                      :connectors connectors
                      :enjins enjins
                      :webservices webservices
                      :jobs jobs)

  (map->simple-enjin {:model* model
                      :application-promise* application-promise
                      :params* (or params {})
                      :connectors* (or connectors {})
                      :enjins* (or enjins {})
                      :webservices* (or webservices {})
                      :jobs* (or jobs {})
                      :running-jobs* (ref {})}))

(def ^:private create-enjin-arg-specs
  {:application-promise true
   :params true
   :connectors true
   :enjins true})

(defn create-enjin
  [model & {:keys [application-promise params connectors enjins] :as args}]

  (util/check-map create-enjin-arg-specs args)

  (let [pmodel (enjm/persist-model model)
        webservices (:webservices pmodel)
        jobs (:jobs pmodel)]

    (create-simple-enjin* :model pmodel
                          :application-promise application-promise
                          :params params
                          :connectors connectors
                          :enjins enjins
                          :webservices webservices
                          :jobs jobs)))

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
  (print-enjin enjin writer))

(defmethod print-dup simple-enjin
  [enjin writer]
  (print-enjin enjin writer))
