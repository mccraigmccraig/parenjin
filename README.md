# clopp

[![Build Status](https://travis-ci.org/mccraigmccraig/clopp.png?branch=master)](https://travis-ci.org/mccraigmccraig/clopp)

Simple application components in clojure

Each component presents a uniform interface defined in terms of queries, jobs, services and webservices, which are all just functions

An Application consists of many Datasets

A Dataset has

* a model : DatasetModel
* some parameters
* some connectors
* some dataset dependencies
* some queries : functions which will be called with the dataset and arbitrary other parameters
* some jobs : long running, but eventually terminating, jobs
* some services : non-terminating services
* some webservices : functions which return a list of compojure routes exposing the data in the Dataset

A DatasetModel defines :

* the parameters a Dataset requires
* the connectors a Dataset requires
* the dependent Datasets a Dataset requires
* the queries
* the jobs
* the services
* the webservices

A DSL is provided to create DatasetModels

    (require '[clopp.dataset :as ds])
    (require '[clopp.dataset-model :as dsm])
    (require '[compojure.core :as compojure])

    (def m (dsm/create-dataset-model :foo))
    (-> m
        (dsm/requires-param :bar String)
        (dsm/requires-connector :postgresql javax.sql.DataSource)
        (dsm/requires-dataset :twitter :twitter-datasource)

        (dsm/defquery :get-foo (fn [dataset foo-id] "do stuff to get a Foo with id foo-id"))
        (dsm/defquery :get-tweets (fn [dataset screen-name]
                                    (ds/query (ds/dataset-dep dataset :twitter) :get-tweets screen-name)))

        (dsm/defjob :process-new-foos (fn [dataset] "do some stuff"))

        (dsm/defservice :retrieve-new-foos (fn [dataset] (->> #(Thread/sleep 1000) repeat (map #(apply % [])) dorun)))

        (dsm/defwebservice :show-foos (fn [dataset] (routes (GET (str (ds/param dataset :bar) "/:foo-id" [foo-id] {:foo-id foo-id}))))))

    (def dsa (dsm/create-dataset :params {:bar "somebar"}
                                 :connectors {:postgresql "get a postgresql DataSource from somewhere"}
                                 :datasets {:twitter "get the twitter Dataset from somewhere"}))

An Application consists of :

* a specification : a clojure datastructure defining how to construct the Application
* a web-connector : a Clomponent with a :routes config parameter which exposes all the specified Dataset webservices
* some datasets : the Applications's datasets and their dependencies are resolved in the specification. circular dependencies are permitted

The application specification is a clojure datastructure which defines how the Application is to get it's web-connector and construct it's Datasets

* `:connectors` is a map of connector objects, which may be referenced by datasets
* `:datasets` is a map of dataset specifications
* `:web` specifies a web-connector, which must be present in the `:connectors` map

For each dataset specification

* `:model` refers to a dataset-model
* `:connectors` specifies how the dataset-model connector requirements are to be satisfied from the app `:connectors` map
* `:params` provides values to satisfy the dataset-model param requirements
* `:dataset-deps` specifies how the dataset-model dataset requirements are to be satisfied from the apps `:datasets`
* `:services` specifies :none, :all, or a list of service-ids for dataset services to be run on app start
* `:webservices` specifies :none, :all or a list of webservice-ids for dataset webservices to be mounted on the app `:web :connector` on app start

    (require '[clopp.application :as app])

    (def app-spec {:connectors :my-project.connectors.registry ;; a map of connector objects by id

                   :datasets {:twitter {:model :my-project.datasets.twitter/model
                                        :connectors {:postgresql :postgresql} ;; maps a connector from the registry to the Datasource id
                                        :params {:tag "myproject"} ;; provides a param for instantiating the Dataset
                                        :services :none            ;; don't run any of the Dataset's services
                                        :webservices :all}         ;; mount all of the Dataset's webservices

                              :foos {:model m
                                     :connectors {:postgresql :postgresql}
                                     :params {:bar "myproject"}
                                     :dataset-deps {:twitter :twitter} ;; maps the :twitter Dataset above to the Datasource's :twitter dependency
                                     :services :all                    ;; run all of the Dataset's services
                                     :webservices [:show-foos]}}       ;; mount just one of the Dataset's webservices
                   :web {:connector :web                      ;; specify the web connector Clomponent
                         :some-param :blah                    ;; additional params given to clomponent/create
                      ;; :routes <routes-from-datasets>       ;; along with routes gotten from datasets
                        }})


    (def app (app/create-application app-spec))

    (app/start app) ;; mount specified webservices, start specified services for each dataset

    (app/stop app)  ;; unmount webservices, stop all jobs and services for each dataset


## Usage

Include the clojars dependency

    (clopp "0.1.0-SNAPSHOT")

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
