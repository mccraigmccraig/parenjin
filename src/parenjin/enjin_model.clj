(ns parenjin.enjin-model
  ;; (:use midje.sweet midje.open-protocols)
  (:require [parenjin.util :as util]))

(defprotocol EnjinModelProtocol
  "EnjinModel is a factory for Enjins of a particular type. an EnjinModel has
     - model-type : a unique identifier for the EnjinModel
     - param-requirements : a map of {param-id type} which defines the ids of required parameters,
                            and their respective types
     - connector-requirements : a map of {connector-id type} which defines the ids of required connectors,
                                and their respective types
     - enjin-requirements : a map of {enjin-id type} which defines the ids of required Enjin dependencies,
                              and their respective types
     - webservices : a map of {webservice-id (fn [enjin] ... (compojure-routes))} which functions must return
                     lists of Compojure routes, and which define webservices on the Enjin"

  (model-type [this]
    "return the model-type key for the enjin")

  (requires-param [this param type] [this param]
    "specify the enjin requires a <param> of <type>")
  (requires-connector [this connector-id object-type] [this connector-id]
    "specify the enjin requires a connector with id <connector-id> and type <type>")
  (requires-enjin [this enjin-id model-type] [this enjin-id]
    "specify the enjin has a dependent enjin with id <enjin-id> and model-type <model-type>")

  (defwebservice [this webservice-id websvcfn]
    "define a webservice on the enjin with id <webservice-id>. <websvcfn> is a function of one parameter, the enjin,
     which returns a list of compojure routes")

  (defjob [this job-id jobfn]
    "define a job on the enjin with id <job-id>. <jobfn> is a function of one parameter, the enjin")

  (persist-model [this]
    "create a persistent version of the EnjinModel"))

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
   [:webservices* :webservices]
   [:jobs* :jobs]])

(defrecord enjin-model
  [model-type* param-reqs* connector-reqs* enjin-reqs* webservices* jobs*]

  EnjinModelProtocol
  (model-type [this]
    model-type*)

  (requires-param [this param] (requires-param this param true))

  (requires-param [this param type]
    (assoc-def this param-reqs* param type))

  (requires-connector [this connector-id] (requires-connector this connector-id true))

  (requires-connector [this connector-id object-type]
    (assoc-def this connector-reqs* connector-id object-type))

  (requires-enjin [this enjin-id] (requires-enjin this enjin-id true))

  (requires-enjin [this enjin-id model-type]
    (assoc-def this enjin-reqs* enjin-id model-type))

  (defwebservice [this webservice-id websvcfn]
    (assoc-def this webservices* webservice-id websvcfn))

  (defjob [this job-id jobfn]
    (assoc-def this jobs* job-id jobfn))

  (persist-model [this]
    (->> persist-model-fields
         (map (fn [[fromf tof]] [tof @(fromf this)]))
         (into {:model-type (:model-type* this)}))))

(defn create-enjin-model
  "create a EnjinModel supplying a <model-type> which should uniquely identify the model"
  [model-type]
  (map->enjin-model {:model-type* model-type
                     :param-reqs* (ref {})
                     :connector-reqs* (ref {})
                     :enjin-reqs* (ref {})
                     :webservices* (ref {})
                     :jobs* (ref {})}))
