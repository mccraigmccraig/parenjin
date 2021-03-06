(ns parenjin.application-test
  (:use midje.sweet
        parenjin.application)
  (:require [parenjin.application :as app]
            [parenjin.application-ref :as aref]
            [parenjin.enjin :as enj]
            [parenjin.enjin-model :as enjm]
            [compojure.core :as compojure]))

(def n (-> (enjm/create-enjin-model :foos)
           (enjm/requires-param :tag String)))

;; FIXME : test without open protocols
;; (with-state-changes [(around :facts (let [spec {:enjins {:foomsA {:model n
;;                                                                   :params {:tag "foomsA"}
;;                                                                   :webservices [:foo :bar]}
;;                                                          :foomsB {:model n
;;                                                                   :params {:tag "foomsB"}}}}
;;                                           app-promise (promise)
;;                                           enjins (#'app/create-enjins {} app-promise spec)
;;                                           foomsA (enjins :foomsA)
;;                                           foomsB (enjins :foomsB)]
;;                                       ?form))]
;;   (fact "create-web-routes* should create web-routes according to an app specification"
;;     (#'app/create-web-routes* spec enjins) => [..fooms-a-route-1.. ..fooms-a-route-2.. ..fooms-b-route-1.. ..fooms-b-route-2..]
;;     (provided
;;       (enj/create-webservices foomsA [:foo :bar]) => [..fooms-a-route-1.. ..fooms-a-route-2..]
;;       (enj/create-webservices foomsB :all) => [..fooms-b-route-1.. ..fooms-b-route-2..])))

(fact "fixup-enjins should replace enjin-ids and the value of ApplicationFixRefs with delays, and leave ApplicationRefs"
  (#'app/fixup-enjins (delay {:foo-enjin ..foo-enjin-delay..
                              :bar-enjin ..bar-enjin-delay..})
                      {:enjin-a #app/ref :enjin-a-ref
                       :enjin-b #app/fix-ref [:enjin-b-ref :foo-enjin]
                       :enjin-c :bar-enjin}) =>

                       {:enjin-a #app/ref :enjin-a-ref
                        :enjin-b (aref/map->application-fix-ref {:fix-ref-name* :enjin-b-ref :fix-ref-value* ..foo-enjin-delay..})
                        :enjin-c ..bar-enjin-delay..})

(with-state-changes [(around :facts (let [spec {:model ..model..
                                                :params {:aparam 10 :anotherparam "boo"}
                                                :connectors {:myx :x
                                                             :myy :y}
                                                :enjins {:mybars :bars
                                                         :mybazs :bazs}}
                                          app-promise (promise)
                                          params (:params spec)
                                          bardsdelay (delay ..bards..)
                                          bazdsdelay (delay ..bazds..)]
                                      ?form))]
  (fact "create-enjin should create a enjin from a specification"

    (#'app/create-enjin {:x ..xconn.. :y ..yconn..}
                        app-promise
                        (delay {:bars bardsdelay  :bazs bazdsdelay})
                        spec) => ..enjin..
                        (provided
                          (enj/create-enjin ..model..
                                            :application-promise app-promise
                                            :params params
                                            :connectors {:myx ..xconn.. :myy ..yconn..}
                                            :enjins {:mybars bardsdelay :mybazs bazdsdelay}) => ..enjin..)))

(with-state-changes [(around :facts (let [spec {:model ..model..
                                                :params {}
                                                :connectors {}
                                                :enjins {:myfoo :foo
                                                         :mybar (aref/map->application-ref {:ref-name* :bar-ref})
                                                         :mybaz (aref/map->application-fix-ref {:fix-ref-name* :baz-ref
                                                                                                :fix-ref-value* :baz})}}
                                          app-promise (promise)]
                                      ?form))]
  (fact "create-enjin should create an enjin from a specification with app/ref and app/fix-refs enjins"

    (#'app/create-enjin {}
                        app-promise
                        (delay {:foo ..foo-enjin-delay..  :baz ..baz-enjin-delay..})
                        spec) => ..enjin..
                        (provided
                          (enj/create-enjin ..model..
                                            :application-promise app-promise
                                            :params {}
                                            :connectors {}
                                            :enjins {:myfoo ..foo-enjin-delay..
                                                     :mybar (aref/map->application-ref {:ref-name* :bar-ref})
                                                     :mybaz (aref/map->application-fix-ref {:fix-ref-name* :baz-ref
                                                                                            :fix-ref-value* ..baz-enjin-delay..})
                                                     }) => ..enjin..)))


(with-state-changes [(around :facts (let [spec {:enjins {:foos {:model ..foos-model..}
                                                         :bars {:model ..bars-model..}}}
                                          app-promise (promise)]
                                      ?form))]
  (fact "create-enjins should create all enjins from specifications"

    (-> (#'app/create-enjins ..connectors.. app-promise spec) :foos) => ..foos-enjin..

    (provided
      (enj/create-enjin ..foos-model.. :application-promise app-promise :params {} :connectors {} :enjins {}) => ..foos-enjin..
      (enj/create-enjin ..bars-model.. :application-promise app-promise :params {} :connectors {} :enjins {}) => ..bars-enjin..)

    (-> (#'app/create-enjins ..connectors.. app-promise spec) :bars) => ..bars-enjin..

    (provided
      (enj/create-enjin ..foos-model.. :application-promise app-promise :params {} :connectors {} :enjins {}) => ..foos-enjin..
      (enj/create-enjin ..bars-model.. :application-promise app-promise :params {} :connectors {} :enjins {}) => ..bars-enjin..)))


(def m (-> (enjm/create-enjin-model :foos)
           (enjm/requires-enjin :other-foos :foos)
           (enjm/requires-param :tag String)))

(with-state-changes [(around :facts (let [spec {:enjins {:foosA {:model m
                                                                 :params {:tag "foosA"}
                                                                 :enjins {:other-foos :foosB}}
                                                         :foosB {:model m
                                                                 :params {:tag "foosB"}
                                                                 :enjins {:other-foos :foosA}}}}
                                          app-promise (promise)
                                          enjins (#'app/create-enjins {} app-promise spec)
                                          foosA (enjins :foosA)
                                          foosB (enjins :foosB)]
                                      ?form))]
  (fact "create-enjins should allow circular dependencies amongst enjins"
    (-> foosA :enjin* :params* :tag) => "foosA"
    (-> foosA :enjin* :enjins* :other-foos deref) => foosB

    (-> foosB :enjin* :params* :tag) => "foosB"
    (-> foosB :enjin* :enjins* :other-foos deref) => foosA))


(fact "create-application-job should assemble a list of enjin jobs"
  (#'app/create-application-job {:foo ..foo-enjin.. :bar ..bar-enjin..} [[:foo :foojob1] [:bar :barjob1]]) =>
  [..foojob1.. ..barjob1..]
  (provided
    (enj/create-job ..foo-enjin.. :foojob1) => ..foojob1..
    (enj/create-job ..bar-enjin.. :barjob1) => ..barjob1..))

(fact "create-application-job should bork if a non-existent enjin is referenced"
  (let [enjins {:foo (enj/map->simple-enjin {:jobs* {:foojob1 ..foojob1..}})}]
    (try
      (#'app/create-application-job enjins [[:foo :foojob1] [:bar :barjob1]])
      (catch Exception e (ex-data e))) => {:enjin-id :bar :job-id :barjob1}))

(fact "create-application-job should bork if a non-existent job is referenced"
  (let [enjins {:foo (enj/map->simple-enjin {:jobs* {:foojob1 ..foojob1..}})}]
    (#'app/create-application-job enjins [[:foo :foojob2]]) =>
    (throws #"job-fn arg is not a fn")))

(fact "create-application-jobs should create a map of application-jobs"
  (#'app/create-application-jobs
   {:foo ..foo-enjin.. :bar ..bar-enjin..}
   {:jobs {:stuff [[:foo :foojob1] [:foo :foojob2] [:bar :barjob1]]
           :otherstuff [[:foo :foojob1] [:bar :barjob2]]}}) =>
           {:stuff [..foojob1.. ..foojob2.. ..barjob1..]
            :otherstuff [..foojob1.. ..barjob2..]}

           (provided
             (enj/create-job ..foo-enjin.. :foojob1) => ..foojob1..
             (enj/create-job ..foo-enjin.. :foojob2) => ..foojob2..
             (enj/create-job ..bar-enjin.. :barjob1) => ..barjob1..
             (enj/create-job ..bar-enjin.. :barjob2) => ..barjob2..))

(declare ..job1..)
(def v (-> (enjm/create-enjin-model :foos)
           (enjm/requires-param :tag String)
           (enjm/defjob :job1 ..job1..)))

(with-state-changes [(around :facts (let [conn {:aconn ..aconn.. :bconn ..bconn..}
                                          spec {:enjins {:foomsA {:model v
                                                                  :params {:tag "foomsA"}
                                                                  :webservices [:foo :bar]}
                                                         :foomsB {:model v
                                                                  :params {:tag "foomsB"}
                                                                  :webservices [:bar :baz]}}
                                                :jobs {:dostuff [[:foomsA :job1]]
                                                       :otherstuff [[:foomsB :job1]]}}
                                          app-promise (promise)
                                          enjs (#'app/create-enjins conn app-promise spec)
                                          foomsA (:foomsA enjs)
                                          foomsB (:foomsB enjs)]
                                      ?form))]
  (fact "create-application* should make the app-spec available"
    (app-spec (#'app/create-application* conn spec)) => spec)

  (fact "create-application* should create enjins"
    (enjins (#'app/create-application* conn spec)) => enjs
    (provided
      (#'app/create-enjins conn anything spec) => enjs))

  ;; FIXME : test without open protocols

  ;; (fact "create-application* should create jobs"
  ;;   (-> (#'app/create-application* conn spec) (job :dostuff) set) => (set [..foomsAjob1..])
  ;;   (provided
  ;;     (#'app/create-enjins conn anything spec) => enjs
  ;;     (enj/create-job foomsA :job1) => ..foomsAjob1..
  ;;     (enj/create-job foomsB :job1) => ..foomsBjob1..)

  ;;   (-> (#'app/create-application* conn spec) (job :otherstuff) set) => (set [..foomsBjob1..])
  ;;   (provided
  ;;     (#'app/create-enjins conn anything spec) => enjs
  ;;     (enj/create-job foomsA :job1) => ..foomsAjob1..
  ;;     (enj/create-job foomsB :job1) => ..foomsBjob1..))
  )


(def app-param-ref-spec {:enjins {:A {:model n
                                      :params {:tag #app/ref :ze-tag}}
                                  :B {:model n
                                      :params {:tag #app/ref :ze-tag}}}})

(with-state-changes [(around :facts (let [app (#'app/create-application* {} app-param-ref-spec)
                                          a (app/enjin app :A)
                                          b (app/enjin app :B)]
                                      ?form))]
  (fact "create-application* should allow binding of params as application-refs"
    (enj/with-params a {:tag "foo"}
      (enj/param a :tag) => "foo"
      (enj/param b :tag) => "foo")

    (enj/with-params b {:tag "foo"}
      (enj/param a :tag) => "foo"
      (enj/param b :tag) => "foo")))

(def app-fix-param-ref-spec {:enjins {:A {:model n
                                          :params {:tag #app/ref :ze-tag}}
                                      :B {:model n
                                          :params {:tag #app/fix-ref [:ze-tag "foo"]}}}})
(with-state-changes [(around :facts (let [app (#'app/create-application* {} app-fix-param-ref-spec)
                                          a (app/enjin app :A)
                                          b (app/enjin app :B)]
                                        ?form))]
    (fact "application should allow fixing of params to application-refs, but should not allow re-binding"
      (enj/param b :tag) => "foo"
      (enj/with-params b {:tag "bar"} (enj/param b :tag)) => (throws RuntimeException  #"can't rebind.*:ze-tag")

      (enj/with-params a {:tag "bar"} (enj/param a :tag)) => "bar"
      (enj/with-params b {}
        (enj/with-params a {:tag "bar"} (enj/param a :tag))) => "bar"))

(def q (-> (enjm/create-enjin-model :foos)
           (enjm/requires-param :a-param)
           (enjm/requires-enjin :some-bars :bars)))
(def r (-> (enjm/create-enjin-model :bars)
           (enjm/requires-param :another-param)))
(def app-fix-param-ref-enjin-dep-spec {:enjins {:A {:model q
                                                    :params {:a-param #app/fix-ref [:a-ref "blah"]}
                                                    :enjins {:some-bars :B}}
                                                :B {:model r
                                                    :params {:another-param #app/ref :a-ref}}}})
(with-state-changes [(around :facts (let [app (#'app/create-application* {} app-fix-param-ref-enjin-dep-spec)
                                          a (app/enjin app :A)
                                          b (app/enjin app :B)]
                                      ?form))]
  (fact "application should allow fixing of params to application-refs across enjin dependency links"
      (enj/param a :a-param) => "blah"
      (-> a
          (enj/enjin :some-bars)
          (enj/param :another-param)) => "blah"))

(def s (-> (enjm/create-enjin-model :foos)
           (enjm/requires-enjin :some-bars :bars)
           (enjm/requires-enjin :some-bazs :bazs)))
(def t (-> (enjm/create-enjin-model :bars)
           (enjm/requires-enjin :some-bazs :bazs)))
(def u (-> (enjm/create-enjin-model :bazs)))
(def app-fix-enjin-ref-enjin-dep-spec {:enjins {:A {:model s
                                                    :enjins {:some-bars :B
                                                             :some-bazs #app/fix-ref [:enj-ref :C]}}
                                                :B {:model t
                                                    :enjins {:some-bazs #app/ref :enj-ref }}
                                                :C {:model u}}})
(with-state-changes [(around :facts (let [app (#'app/create-application* {} app-fix-enjin-ref-enjin-dep-spec)
                                          a (app/enjin app :A)
                                          b (app/enjin app :B)
                                          c (app/enjin app :C)]
                                      ?form))]
  (fact "application should allow fixing of enjins to application-refs across enjin dependency links"
    (-> a (enj/enjin :some-bars) :enjin*) => (:enjin* b)
    (-> a (enj/enjin :some-bazs) :enjin*) => (:enjin* c)

    (-> a (enj/enjin :some-bars) (enj/enjin :some-bazs) :enjin*) => (:enjin* c)
    (-> b (enj/enjin :some-bazs)) => (throws RuntimeException #"required to be.*:bazs")
))

(def o (-> (enjm/create-enjin-model :foos)
           (enjm/requires-enjin :some-bars :bars)))

(def p (-> (enjm/create-enjin-model :bars)
           (enjm/requires-param :of)))

(def app-enjin-ref-spec {:enjins {:A {:model o
                                      :enjins {:some-bars #app/ref :ze-other-bars}}
                                  :B {:model p
                                      :params {:of #app/ref :ze-other-bars}}}})

(with-state-changes [(around :facts (let [app (#'app/create-application* {} app-enjin-ref-spec)
                                          a (app/enjin app :A)
                                          b (app/enjin app :B)]
                                      ?form))]
  (fact "create-application* should allow binding of enjins as application-refs"
    (enj/with-params b {:of b}

      (enj/enjin a :some-bars) => b)))


(with-state-changes [(around :facts (let [app-proxy (create-application {} ..app-spec..)]
                                      ?form))]
  (fact "the app-spec should be stored in the proxy"
    (app-spec app-proxy) => ..app-spec..))

(with-state-changes [(around :facts (let [app-proxy (create-application ..connectors.. ..app-spec..)]
                                      ?form))]
(fact "create* should create an application if necessary"

  (#'app/create app-proxy) => ..app..
  (provided
    (#'app/create-application* ..connectors.. ..app-spec..) => ..app..)))

(with-state-changes [(around :facts (let [app-proxy (app/map->application-proxy {:connectors* ..connectors..
                                                                                 :app-spec* ..app-spec..
                                                                                 :app* (atom ..app..)})]
                                      ?form))]
  (fact "create* should return an existing application"
    (#'app/create app-proxy) => ..app..))

(fact "create-webservice* should create a production-mode webservice"
  (#'app/create-webservice* ..app.. false) => ..webservice..
  (provided
    (#'app/create-web-routes ..app..) => [..route.. ..another-route..]
    (compojure/routes ..route.. ..another-route..) => ..webservice..))

(fact "create-webservice* should create a dev-mode webservice"
  ((#'app/create-webservice* ..app.. true) ..request..) =>  ..webservice-result..
  (provided
    (app/destroy ..app..) => ..app..
    (app/create-web-routes ..app..) => [..route.. ..another-route..]
    (compojure/routing ..request.. ..route.. ..another-route..) => ..webservice-result..))
