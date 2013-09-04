(ns parenjin.application-test
  (:use midje.sweet
        parenjin.application)
  (:require [parenjin.application :as app]
            [parenjin.enjin :as enj]
            [parenjin.enjin-model :as enjm]
            [compojure.core :as compojure]
            [clomponents.control :as clomp]))

(with-state-changes [(around :facts (let [spec {:model ..model..
                                                :params {:aparam 10 :anotherparam "boo"}
                                                :connectors {:myx :x
                                                             :myy :y}
                                                :enjin-deps {:mybars :bars
                                                               :mybazs :bazs}}
                                          params (:params spec)
                                          bardsdelay (delay ..bards..)
                                          bazdsdelay (delay ..bazds..)]
                                      ?form))]
  (fact "create-enjin should create a enjin from a specification"

    (#'app/create-enjin {:x ..xconn.. :y ..yconn..}
                          (delay {:bars bardsdelay  :bazs bazdsdelay})
                          spec) => ..enjin..
                          (provided
                            (enj/create-enjin ..model..
                                              :params params
                                              :connectors {:myx ..xconn.. :myy ..yconn..}
                                              :enjin-deps {:mybars bardsdelay :mybazs bazdsdelay}) => ..enjin..)))

(with-state-changes [(around :facts (let [spec {:enjins {:foos {:model ..foos-model..}
                                                         :bars {:model ..bars-model..}}}]
                                      ?form))]
  (fact "create-enjins should create all enjins from specifications"

    (-> (#'app/create-enjins ..connectors.. spec) :foos) => ..foos-enjin..

    (provided
      (enj/create-enjin ..foos-model.. :params {} :connectors {} :enjin-deps {}) => ..foos-enjin..
      (enj/create-enjin ..bars-model.. :params {} :connectors {} :enjin-deps {}) => ..bars-enjin..)

    (-> (#'app/create-enjins ..connectors.. spec) :bars) => ..bars-enjin..

    (provided
      (enj/create-enjin ..foos-model.. :params {} :connectors {} :enjin-deps {}) => ..foos-enjin..
      (enj/create-enjin ..bars-model.. :params {} :connectors {} :enjin-deps {}) => ..bars-enjin..)))


(def m (-> (enjm/create-enjin-model :foos)
           (enjm/requires-enjin :other-foos :foos)
           (enjm/requires-param :tag String)))

(with-state-changes [(around :facts (let [spec {:enjins {:foosA {:model m
                                                                 :params {:tag "foosA"}
                                                                 :enjin-deps {:other-foos :foosB}}
                                                         :foosB {:model m
                                                                 :params {:tag "foosB"}
                                                                 :enjin-deps {:other-foos :foosA}}}}
                                          enjins (#'app/create-enjins {} spec)
                                          foosA (enjins :foosA)
                                          foosB (enjins :foosB)]
                                      ?form))]
  (fact "create-enjins should allow circular dependencies amongst enjins"
    (-> foosA :params* :tag) => "foosA"
    (-> foosA :enjin-deps* :other-foos deref) => foosB

    (-> foosB :params* :tag) => "foosB"
    (-> foosB :enjin-deps* :other-foos deref) => foosA))

(def n (-> (enjm/create-enjin-model :fooms)
           (enjm/requires-param :tag String)))

(with-state-changes [(around :facts (let [spec {:enjins {:foomsA {:model n
                                                                   :params {:tag "foomsA"}
                                                                   :webservices [:foo :bar]}
                                                           :foomsB {:model n
                                                                   :params {:tag "foomsB"}}}}
                                          enjins (#'app/create-enjins {} spec)
                                          foomsA (enjins :foomsA)
                                          foomsB (enjins :foomsB)]
                                      ?form))]
  (fact "create-web-routes* should create web-routes according to an app specification"
    (#'app/create-web-routes* spec enjins) => [..fooms-a-route-1.. ..fooms-a-route-2.. ..fooms-b-route-1.. ..fooms-b-route-2..]
    (provided
      (enj/create-webservices foomsA [:foo :bar]) => [..fooms-a-route-1.. ..fooms-a-route-2..]
      (enj/create-webservices foomsB :all) => [..fooms-b-route-1.. ..fooms-b-route-2..])))

(with-state-changes [(around :facts (let [spec {:connectors {:aconn ..aconn.. :bconn ..bconn.. :webconn ..web-connector..}
                                                :enjins {:foomsA {:model n
                                                                  :params {:tag "foomsA"}
                                                                  :webservices [:foo :bar]}
                                                         :foomsB {:model n
                                                                  :params {:tag "foomsB"}
                                                                  :webservices [:bar :baz]}}
                                                :web {:connector :webconn}}
                                          conn (:connectors spec)
                                          enjs (#'app/create-enjins {} spec)]

                                      ?form))]
  (fact "create-application* should create an application from a specification"
    (app-spec (#'app/create-application* spec)) => spec
    (web-connector (#'app/create-application* spec)) => ..web-connector..
    (enjins (#'app/create-application* spec)) => enjs

    (provided
      (#'app/create-enjins conn spec) => enjs)))

(with-state-changes [(around :facts (let [model (-> (enjm/create-enjin-model :fooms)
                                                    (enjm/requires-param :tag String))

                                          spec {:connectors {:aconn ..aconn.. :bconn ..bconn.. :webconn ..webconn..}
                                                :enjins {:foomsA {:model model
                                                                    :params {:tag "foomsA"}
                                                                    :services ..fooms-a-services..
                                                                    :webservices [:foo :bar]}
                                                           :foomsB {:model model
                                                                    :params {:tag "foomsB"}
                                                                    :services ..fooms-b-services..
                                                                    :webservices [:bar :baz]}}
                                                :web {:connector :webconn}}
                                          conn (:connectors spec)
                                          ds (#'app/create-enjins conn spec)
                                          foomsA (:foomsA ds)
                                          foomsB (:foomsB ds)]
                                      ?form))]
  (fact "start* should start services, and mount webservices from contained enjins"
    (-> (#'app/create-application* spec)
        (start* ..proxy..)) => true?

    (provided
      (#'app/create-enjins conn spec) => ds

      (enj/start-services foomsA ..fooms-a-services..) => true
      (enj/start-services foomsB ..fooms-b-services..) => true
      (clomp/destroy ..webconn..) => true
      (clomp/create ..webconn.. {:connector :webconn :app ..proxy..}) => true)))

(with-state-changes [(around :facts (let [model (-> (enjm/create-enjin-model :fooms)
                                                    (enjm/requires-param :tag String))

                                          spec {:connectors {:aconn ..aconn.. :bconn ..bconn.. :webconn ..web-connector..}
                                                :enjins {:foomsA {:model model
                                                                    :params {:tag "foomsA"}
                                                                    :services ..fooms-a-services..
                                                                    :webservices [:foo :bar]}
                                                           :foomsB {:model model
                                                                    :params {:tag "foomsB"}
                                                                    :services ..fooms-b-services..
                                                                    :webservices [:bar :baz]}}
                                                :web {:connector :webconn}}
                                          conn (:connectors spec)
                                          ds (#'app/create-enjins conn spec)
                                          foomsA (:foomsA ds)
                                          foomsB (:foomsB ds)]
                                      ?form))]
  (fact "stop should stop services, jobs, and unmount webservices from contained enjins"
    (stop (#'app/create-application spec)) => true?

    (provided
      (#'app/create-enjins conn spec) => ds

      (enj/stop-services foomsA :all) => true
      (enj/stop-services foomsB :all) => true
      (clomp/destroy ..web-connector..) => true)))


(with-state-changes [(around :facts (let [app-proxy (create-application ..app-spec..)]
                                      ?form))]
  (fact "the app-spec should be stored in the proxy"
    (app-spec app-proxy) => ..app-spec..))

(with-state-changes [(around :facts (let [app-proxy (create-application ..app-spec..)]
                                      ?form))]
  (fact "create should create an app to proxy to"

    @(:app* (create app-proxy)) => ..app..
    (provided
      (#'app/create-application* ..app-spec..) => ..app..)))

;; ;; don't know how to test this : midje acts funny if the method under test is
;; ;; also in a provided block


;; ;; (with-state-changes [(around :facts (let [app-proxy (create-application ..app-spec..)]
;; ;;                                       ?form))]
;; ;;   (fact "web-connector should create the app and proxy to it"
;; ;;     (web-connector app-proxy) => ..web-conn..
;; ;;     (provided
;; ;;       (#'app/create-application* ..app-spec..) => ..app..
;; ;;       (web-connector ..app..) => ..web-conn..)))

;; ;; (with-state-changes [(around :facts (let [app-proxy (create-application ..app-spec..)]
;; ;;                                       ?form))]
;; ;;   (fact "create-web-routes should create the app and proxy to it"
;; ;;     (create-web-routes app-proxy) => ..web-routes..
;; ;;     (provided
;; ;;       (#'app/create-application* ..app-spec..) => ..app..
;; ;;       (create-web-routes ..app..) => ..web-routes..
;; ;;       )))
