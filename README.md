# parenjin

[![Build Status](https://travis-ci.org/mccraigmccraig/parenjin.png?branch=master)](https://travis-ci.org/mccraigmccraig/parenjin)

A component container aimed at Compojure web apps, with dynamic parameter and dependency management

* An application is decomposed into Enjins
* Each Enjin defines it's runtime requirements as an EnjinModel, which is created with a DSL
* Instances of Enjins are created and wired together at runtime by an application specification, which is just a map
* Enjin parameters can be wired to dynamic Application parameters
* Enjin parameters can be declaratively fixed for some Enjin, which fixes them also
  for that Enjin's dependencies
* Application jobs can be defined as sequences of Enjin jobs (delcared functions of an Enjin instance).
  Application jobs can be run in serial or parallel

## EnjinModel

An EnjinModel specifies the runtime requirements of Enjin instances created from it

* parameters : to be provided at application assembly time, or runtime
* connectors : arbitrary, generally heavyweight, objects to be resolved at application assembly time
* Enjin dependencies : other Enjins, with resolutions defined at application assembly time, or runtime
* webservices : functions of a single Enjin parameter which return Compojure routes
* jobs : functions of a single Enjin parameter

A DSL is provided to create EnjinModels

    (require '[parenjin.enjin :as enj])
    (require '[parenjin.enjin-model :as enjm])
    (require '[compojure.core :as compojure])

    (def m (enjm/create-enjin-model :foo))
    (-> m
        (enjm/requires-param :prefix [nil String])                 ;; :prefix param is nil or a string
        (enjm/requires-param :bar)                                 ;; :bar param is unchecked
        (enjm/requires-connector :postgresql javax.sql.DataSource) ;; :postgresql connector is a javax.sql.Datasource
        (enjm/requires-enjin :twitter :twitter-datasource)         ;; :twitter enjin has type :twitter-datasource

        (enjm/defwebservice :show-foos (fn [enjin] (routes (GET (str (enj/param enjin :bar) "/:foo-id" [foo-id] {:foo-id foo-id})))))

        (enjm/defjob :update foo/update))

## Application

An Application specification is a map which defines how Enjin instances are to be created and how their runtime requirements are to be resolved

### Connectors

Connectors are resolved from a map supplied at Application assembly time

### Parameters

Parameters can be resolved in several ways :

* static : a static value
* #app/ref : a dynamic reference to an application parameter : can be set in code or by an #app/fix-ref
* #app/fix-ref : fixes a reference to an application parameter for an Enjin and all it's dependencies

### Enjin dependencies

Enjin dependencies can be resolved in several ways :

* static : a static referene to another Enjin in the Application
* #app/ref : a dynamic reference to another Enjin : can be set in code or by an #app/fix-ref
* #app/fix-ref : fixes a reference to an application parameter for an Enjin and all it's dependencies

### Webservices

Webservices declared in Enjins can be declaratively mapped into an Application webservice

### Jobs

Application jobs are a sequence of Enjin Job mappings, specified as a sequence of [:enjin-key :enjin-job-key] pairs. Application Jobs
can be run in two ways :

* in serial : a Job is started after the previous Job in the sequence finishes
* in parallel : all Jobs in the sequence are started simultaneously on different Threads

        (require '[parenjin.application :as app])
        (require '[parenjin.job :as job])

        (def app-spec {:enjins {:twitter {:model :my-project.enjins.twitter/model
                                          :connectors {:postgresql :postgresql}    ;; maps a connector from the registry to the Enjin's id
                                          :params {:prefix #app/ref :app-prefix}   ;; references the application param :app-prefix
                                          :webservices :all}                       ;; mount all of the Enjin's webservices

                                :foos {:model m
                                       :connectors {:postgresql :postgresql}
                                       :params {:prefix #app/fix-ref [:app-prefix "FOOP"] ;; fixes the application param :app-prefix to "FOOP" for this
                                                                                          ;; Enjin instance, *and* it's dependencies
                                                :bar "myproject"}              ;; provides a literal value
                                       :enjins {:twitter :twitter}             ;; maps the :twitter Enjin above to the Enjin's :twitter dependency
                                       :webservices [:show-foos]}}             ;; mount just one of the Enjin's webservices

                       :jobs {:update [[:foos :update]]
                               }})

        (def app (app/create-application connectors app-spec))


        (def routes (app/create-webservice app true)) ;; create a dev-mode route which will recreate the app and all it's
                                                      ;; routes on every request

        (def twitter (app/enjin app :twitter))
        (def foos (app/enjin app :foos))

        ;; setting an application param dynamically
        (enj/with-params twitter {:prefix "BOO"}
          (enj/param twitter :prefix)) ;; => "BOO"

        ;; application param is declaratively fixed for an Enjin and it's dependencies
        (enj/param foos :prefix) ;; => "FOOP"
        (def foos-twitter (enj/enjin foos :twitter))
        (enj/param foos-twitter :prefix) ;; => "FOOP"

        ;; run the :update job serially
        (job/run-jobs-serial (app/job app :update))

## Usage

Include the clojars dependency

    (parenjin "0.4.0

## License

Copyright © 2013 mccraigmccraig of the clan mccraig

Distributed under the Eclipse Public License, the same as Clojure.
