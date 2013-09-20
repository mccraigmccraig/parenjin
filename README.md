# parenjin

[![Build Status](https://travis-ci.org/mccraigmccraig/parenjin.png?branch=master)](https://travis-ci.org/mccraigmccraig/parenjin)

Parameterisable application Engines for Compojure

Decompose your application into Enjins, then construct Applications by declaratively wiring Enjins together with configuration parameters and connectors

This helps with :

* sharing data resources cleanly inside an application
* building multiple applications on the same platform which share some resources
* multi-tenant applications, where each tenant's view can be defined by some configuration

An Application consists of :

* a specification : a clojure datastructure defining how to construct the Application
* some Enjins : the Applications's Enjins and their dependencies are resolved in the specification. circular dependencies are permitted

The application specification is a clojure datastructure which defines how the Application is to construct and connect it's Enjins, and which webservices from which Enjin's are to be exposed

* `:enjins` is a map of Enjin specifications

For each Enjin specification

* `:model` refers to an EnjinModel, which defines the Enjins requirements
* `:connectors` specifies how the Enjin connector requirements are to be satisfied from the application's `:connectors` map
* `:params` provides values to satisfy the Enjin param requirements. the #app/ref reader-macro can be used to create an application reference,
   which can be set dynamically. All references (from any Enjin in the same application) to the same application reference refer to the
   same value, so Enjins can be declared to share dynamic parameters
* `:enjins` specifies how the Enjin dependency requirements are to be satisfied from the application's `:enjins`. an Enjin dependency may be
   set as an application reference, so one Enjin can dynamically satisfy the Enjin dependencies of another
* `:webservices` specifies :none, :all or a list of webservice-ids for Enjin webservices to be included in the application webservice from create-webservice

        (require '[parenjin.application :as app])

        (def app-spec {:enjins {:twitter {:model :my-project.enjins.twitter/model
                                          :connectors {:postgresql :postgresql}    ;; maps a connector from the registry to the Enjin's id
                                          :params {:prefix #app/ref :app-prefix} ;; references the application param :app-prefix
                                          :webservices :all}                       ;; mount all of the Enjin's webservices

                                :foos {:model m
                                       :connectors {:postgresql :postgresql}
                                       :params {:prefix #app/ref :app-prefix ;; references the application param :app-prefix
                                                :bar "myproject"}              ;; provides a literal value
                                       :enjins {:twitter :twitter}             ;; maps the :twitter Enjin above to the Enjin's :twitter dependency
                                       :webservices [:show-foos]}}             ;; mount just one of the Enjin's webservices
                               })

        (def app (app/create-application connectors app-spec))


        (def routes (app/create-webservice app true)) ;; create a dev-mode route which will recreate the app and all it's
                                                      ;; routes on every request

        (def twitter (app/enjin app :twitter))
        (def foos (app/enjin app :foos))

        (enj/with-params foos {:prefix "BOO"}
          (enj/param foos :prefix)     ;; => "BOO" ;; Enjin reference params can be set dynamically
          (enj/param twitter :prefix)  ;; => "BOO" ;; and all other Enjins sharing the same reference param will see the value
        )

Enjins and Applications are lightweight objects, and can reasonably be constructed on every request : All the heavyweight objects belong to the connectors registry or the EnjinModels

An EnjinModel defines :

* the parameters an Enjin requires
* the connectors an Enjin requires
* the dependent Enjins an Enjin requires
* the webservices : functions of a single (Enjin) parameter which return Compojure routes

A DSL is provided to create EnjinModels

    (require '[parenjin.enjin :as enj])
    (require '[parenjin.enjin-model :as enjm])
    (require '[compojure.core :as compojure])

    (def m (enjm/create-enjin-model :foo))
    (-> m
        (enjm/requires-param :prefix String)
        (enjm/requires-param :bar String)
        (enjm/requires-connector :postgresql javax.sql.DataSource)
        (enjm/requires-enjin :twitter :twitter-datasource)

        (enjm/defwebservice :show-foos (fn [enjin] (routes (GET (str (enj/param enjin :bar) "/:foo-id" [foo-id] {:foo-id foo-id}))))))



## Usage

Include the clojars dependency

    (parenjin "0.3.0-SNAPSHOT")

## License

Copyright © 2013 mccraigmccraig of the clan mccraig

Distributed under the Eclipse Public License, the same as Clojure.
