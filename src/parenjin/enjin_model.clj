(ns parenjin.enjin-model
  (:use midje.open-protocols)
  (:require [parenjin.util :as util]
            [parenjin.enjin :as enj]))

(defprotocol EnjinModel
  "EnjinModel is a factory for Enjins of a particular type. an EnjinModel has
     - model-type : a unique identifier for the EnjinModel
     - param-requirements : a map of {param-id type} which defines the ids of required parameters,
                            and their respective types
     - connector-requirements : a map of {connector-id type} which defines the ids of required connectors,
                                and their respective types
     - enjin-requirements : a map of {enjin-id type} which defines the ids of required Enjin dependencies,
                              and their respective types
     - jobs : a map of {job-id (fn [enjin] ...)} which defines long-running jobs on the Enjin
     - services :  a map of {service-id (fn [enjin] ...)} which defines non-terminating services for the Enjin
     - webservices : a map of {webservice-id (fn [enjin] ... (compojure-routes))} which functions must return
                     lists of Compojure routes, and which define webservices on the Enjin"

  (model-type [this]
    "return the model-type key for the enjin")

  (requires-param [this param type]
    "specify the enjin requires a <param> of <type>")
  (requires-connector [this connector-id object-type]
    "specify the enjin requires a connector with id <connector-id> and type <type>")
  (requires-enjin [this enjin-id model-type]
    "specify the enjin has a dependent enjin with id <enjin-id> and model-type <model-type>")

  (defjob [this job-id jobfn]
    "define a job on the enjin with id <job-id>. <jobfn> is a function of one parameter, the enjin")

  (defservice [this service-id servicefn]
    "define a service on the enjin with id <service-id>. <servicefn> is a function of one parameter, the enjin")

  (defwebservice [this webservice-id websvcfn]
    "define a webservice on the enjin with id <webservice-id>. <websvcfn> is a function of one parameter, the enjin,
     which returns a list of compojure routes")

  (create-enjin* [this params connectors enjins]
    "create a Enjin from this EnjinModel, supplying <params>, <connectors> and <enjins> to match the requirements
     defined in this EnjinModel"))

(defn- assoc-def
  "associate a DSL definition with the right map in a EnjinModel, and return the EnjinModel"
  [this defref key & others]
  (do
    (dosync
     (ref-set defref (apply assoc @defref key others)))
    this))

(def ^:private persist-model-fields
  [[:param-reqs* :param-reqs]
   [:connector-reqs* :connector-reqs]
   [:enjin-reqs* :enjin-reqs]
   [:jobs* :jobs]
   [:services* :services]
   [:webservices* :webservices]]
)
(defn- persist-model
  "produce a version of the enjin model without the refs"
  [enjin-model]
  (->> persist-model-fields
       (map (fn [[fromf tof]] [tof @(fromf enjin-model)]))
       (into {:model-type (:model-type* enjin-model)})))

(defrecord-openly enjin-model
  [model-type* param-reqs* connector-reqs* enjin-reqs* jobs* services* webservices*]

  EnjinModel
  (model-type [this]
    model-type*)

  (requires-param [this param type]
    (assoc-def this param-reqs* param type))

  (requires-connector [this connector-id object-type]
    (assoc-def this connector-reqs* connector-id object-type))

  (requires-enjin [this enjin-id model-type]
    (assoc-def this enjin-reqs* enjin-id model-type))

  (defjob [this job-id jobfn]
    (assoc-def this jobs* job-id jobfn))

  (defservice [this service-id servicefn]
    (assoc-def this services* service-id servicefn))

  (defwebservice [this webservice-id websvcfn]
    (assoc-def this webservices* webservice-id websvcfn))

  (create-enjin* [this params connectors enjin-deps]
    (#'enj/create-simple-enjin* :model (persist-model this)
                                 :params params
                                 :connectors connectors
                                 :enjin-deps enjin-deps
                                 :jobs @jobs*
                                 :services @services*
                                 :webservices @webservices*)))

(def ^:private create-enjin-arg-specs
  {:params [nil map?]
   :connectors [nil map?]
   :enjin-deps [nil map?]})

(defn create-enjin
  "create a Enjin from <enjin-model>,
   supplying <params>, <connectors> and <enjin-deps> to match the requirements
   defined in <enjin-model>"
  [enjin-model & {:keys [params connectors enjin-deps] :as args}]

  (util/check-map create-enjin-arg-specs args)

  (create-enjin* enjin-model params connectors enjin-deps))

(defn create-enjin-model
  "create a EnjinModel supplying a <model-type> which should uniquely identify the model"
  [model-type]
  (map->enjin-model {:model-type* model-type
                       :param-reqs* (ref {})
                       :connector-reqs* (ref {})
                       :enjin-reqs* (ref {})
                       :jobs* (ref {})
                       :services* (ref {})
                       :webservices* (ref {})}))
