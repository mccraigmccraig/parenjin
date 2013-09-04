# clopp

[![Build Status](https://travis-ci.org/mccraigmccraig/parenjin.png?branch=master)](https://travis-ci.org/mccraigmccraig/parenjin)

Parameterisable application Enjins in clojure

Each Enjin presents a uniform interface defined in terms of jobs, services and webservices, which are all just functions

An Application consists of many Enjins

An Enjin has

* a model : EnjinModel
* some parameters
* some connectors
* some enjin dependencies
* some jobs : long running, but eventually terminating, jobs
* some services : non-terminating services
* some webservices : functions which return a list of compojure routes exposing the data in the Dataset

An EnjinModel defines :

* the parameters an Enjin requires
* the connectors an Enjin requires
* the dependent Enjins an Enjin requires
* the jobs
* the services
* the webservices

A DSL is provided to create EnjinModels

    (require '[parenjin.enjin :as enj])
    (require '[parenjin.enjin-model :as enjm])
    (require '[compojure.core :as compojure])

    (def m (enjm/create-enjin-model :foo))
    (-> m
        (enjm/requires-param :bar String)
        (enjm/requires-connector :postgresql javax.sql.DataSource)
        (enjm/requires-enjin :twitter :twitter-datasource)

        (enjm/defjob :process-new-foos (fn [enjin] "do some stuff"))
        (enjm/defservice :retrieve-new-foos (fn [enjin] (->> #(Thread/sleep 1000) repeat (map #(apply % [])) dorun)))

        (enjm/defwebservice :show-foos (fn [enjin] (routes (GET (str (enj/param enjin :bar) "/:foo-id" [foo-id] {:foo-id foo-id}))))))

    (def enja (enjm/create-enjin :params {:bar "somebar"}
                                 :connectors {:postgresql "get a postgresql DataSource from somewhere"}
                                 :enjins {:twitter "get the twitter Enjin from somewhere"}))

An Application consists of :

* a specification : a clojure datastructure defining how to construct the Application
* a web-connector : a Clomponent with a :routes config parameter which exposes all the specified Dataset webservices
* some Enjins : the Applications's Enjins and their dependencies are resolved in the specification. circular dependencies are permitted

The application specification is a clojure datastructure which defines how the Application is to get it's web-connector and construct it's Enjins

* `:connectors` is a map of connector objects, which may be referenced by datasets
* `:datasets` is a map of dataset specifications
* `:web` specifies a web-connector, which must be present in the `:connectors` map

For each Enjin specification

* `:model` refers to an EnjinModel
* `:connectors` specifies how the Enjin connector requirements are to be satisfied from the app `:connectors` map
* `:params` provides values to satisfy the Enjin param requirements
* `:enjin-deps` specifies how the Enjin dependency requirements are to be satisfied from the apps `:datasets`
* `:services` specifies :none, :all, or a list of service-ids for Enjin services to be run on app start
* `:webservices` specifies :none, :all or a list of webservice-ids for Enjin webservices to be mounted on the app `:web :connector` on app start

    (require '[parenjin.application :as app])

    (def app-spec {:connectors :my-project.connectors.registry ;; a map of connector objects by id

                   :enjins {:twitter {:model :my-project.enjins.twitter/model
                                      :connectors {:postgresql :postgresql} ;; maps a connector from the registry to the Datasource id
                                      :params {:tag "myproject"} ;; provides a param for instantiating the Enjin
                                      :services :none            ;; don't run any of the Enjin's services
                                      :webservices :all}         ;; mount all of the Enjin's webservices

                            :foos {:model m
                                   :connectors {:postgresql :postgresql}
                                   :params {:bar "myproject"}
                                   :enjin-deps {:twitter :twitter} ;; maps the :twitter Enjin above to the Datasource's :twitter dependency
                                   :services :all                    ;; run all of the Enjin's services
                                   :webservices [:show-foos]}}       ;; mount just one of the Enjin's webservices
                   :web {:connector :web                      ;; specify the web connector Clomponent
                         :some-param :blah                    ;; additional params given to clomponent/create
                      ;; :routes <routes-from-enjins>       ;; along with routes gotten from enjins
                        }})


    (def app (app/create-application app-spec))

    (app/start app) ;; mount specified webservices, start specified services for each enjin

    (app/stop app)  ;; unmount webservices, stop all jobs and services for each enjin


## Usage

Include the clojars dependency

    (parenjin "0.1.0-SNAPSHOT")

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
