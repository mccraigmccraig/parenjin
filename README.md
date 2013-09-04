# parenjin

[![Build Status](https://travis-ci.org/mccraigmccraig/parenjin.png?branch=master)](https://travis-ci.org/mccraigmccraig/parenjin)

Parameterisable application Enjins for Compojure

The idea is that application function can be decomposed into Enjins, and the Enjins can be assembled together from a datastructure description.

Enjins and Applications are lightweight objects, and can reasonably be constructed on every request

Each Enjin presents a uniform interface defined in terms of jobs, services and webservices, which are all just functions

An Application has many Enjins, and an Enjin has

* a model : EnjinModel : the model determines which other attributes are required
* some parameters
* some connectors : to connect to datasources
* some Enjin dependencies : other Enjins it needs
* some jobs : long running, but eventually terminating, tasks
* some services : non-terminating services
* some webservices : functions which return a list of compojure routes exposing the data in the Enjin

An EnjinModel defines :

* the parameters an Enjin requires
* the connectors an Enjin requires
* the dependent Enjins an Enjin requires
* the jobs : functions of a single (Enjin) parameter
* the services : functions of a single (Enjin) parameter
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

        (enjm/defjob :process-new-foos (fn [enjin] "do some stuff"))
        (enjm/defservice :retrieve-new-foos (fn [enjin] (->> #(Thread/sleep 1000) repeat (map #(apply % [])) dorun)))

        (enjm/defwebservice :show-foos (fn [enjin] (routes (GET (str (enj/param enjin :bar) "/:foo-id" [foo-id] {:foo-id foo-id}))))))

An Application consists of :

* a specification : a clojure datastructure defining how to construct the Application
* a web-connector : a Clomponent with a :routes config parameter which exposes all the specified Enjin webservices
* some Enjins : the Applications's Enjins and their dependencies are resolved in the specification. circular dependencies are permitted

The application specification is a clojure datastructure which defines how the Application is to get it's web-connector and construct it's Enjins

* `:connectors` is a map of connector objects, which may be referenced by Enjins
* `:enjins` is a map of Enjin specifications
* `:web` specifies a web-connector, which must be present in the `:connectors` map

For each Enjin specification

* `:model` refers to an EnjinModel
* `:connectors` specifies how the Enjin connector requirements are to be satisfied from the app `:connectors` map
* `:params` provides values to satisfy the Enjin param requirements. the #app/param reader-macro can be used to create a reference to an
   application parameter, which can be set dynamically. All references (from any Enjin) to the same application parameter refer to the
   same value, so Enjins can be declared to share paramters
* `:enjin-deps` specifies how the Enjin dependency requirements are to be satisfied from the apps `:enjins`
* `:services` specifies :none, :all, or a list of service-ids for Enjin services to be run on app start
* `:webservices` specifies :none, :all or a list of webservice-ids for Enjin webservices to be mounted on the app `:web :connector` on app start

        (require '[parenjin.application :as app])

        (def app-spec {:connectors :my-project.connectors.registry ;; a map of connector objects by id
                       :enjins {:twitter {:model :my-project.enjins.twitter/model
                                          :connectors {:postgresql :postgresql} ;; maps a connector from the registry to the Datasource id
                                          :params {:prefix #app/param :app-prefix} ;; references the application param :app-prefix
                                          :services :none            ;; don't run any of the Enjin's services
                                          :webservices :all}         ;; mount all of the Enjin's webservices

                                :foos {:model m
                                       :connectors {:postgresql :postgresql}
                                       :params {:prefix #app/param :app-prefix  ;; references the application param :app-prefix
                                                :bar "myproject"}               ;; provides a literal value
                                       :enjin-deps {:twitter :twitter} ;; maps the :twitter Enjin above to the Enjin's :twitter dependency
                                       :services :all                    ;; run all of the Enjin's services
                                       :webservices [:show-foos]}}       ;; mount just one of the Enjin's webservices
                       :web {:connector :web                      ;; specify the web connector Clomponent
                             :some-param :blah                    ;; additional params given to clomponent/create
                          ;; :routes <routes-from-enjins>       ;; along with routes gotten from enjins
                            }})

        (def app (app/create-application app-spec))

        (app/start app) ;; mount specified webservices, start specified services for each enjin
        (app/stop app)  ;; unmount webservices, stop all jobs and services for each enjin

        (def twitter (app/enjin app :twitter))
        (def foos (app/enjin app :foos))

        (enj/with-params foos {:prefix "BOO"}
          (enj/param foos :prefix)     ;; => "BOO" ;; Enjin reference params can be set dynamically
          (enj/param twitter :prefix)  ;; => "BOO" ;; and all other Enjins sharing the same reference param will see the value
        )

## Usage

Include the clojars dependency

    (parenjin "0.1.0-SNAPSHOT")

## License

Copyright © 2013 mccraigmccraig of the clan mccraig

Distributed under the Eclipse Public License, the same as Clojure.
