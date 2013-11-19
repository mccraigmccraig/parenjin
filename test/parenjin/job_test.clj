(ns parenjin.job-test
  (:use midje.sweet
        parenjin.job)
  (:require [parenjin.util :as util])
  (:import [java.util.concurrent CancellationException]))

(with-state-changes [(around :facts (let [enjin-promise (promise)
                                          _ (deliver enjin-promise ..enjin..)
                                          result (promise)
                                          trackref (ref nil)]
                                         ?form ))]
  (fact "start-or-noop should start a never started task"
    (start-or-noop enjin-promise trackref (fn [enjin] enjin => ..enjin.. (deliver result ..result..) (Thread/sleep 50))) => :running
    (deref result 0 ..fail..) => ..result..))

(with-state-changes [(around :facts (let [enjin-promise (promise)
                                          _ (deliver enjin-promise ..enjin..)
                                          result (promise)
                                          trackref (ref (future (Thread/sleep 100) (deliver result ..result..)))]
                                         ?form ))]
  (fact "start-or-noop should do nothing to a running task"
    (start-or-noop enjin-promise trackref (fn [enjin] (deliver result ..fail..))) => :running
    (deref result 100 ..nocall..) => ..result..))

(with-state-changes [(around :facts (let [enjin-promise (promise)
                                          _ (deliver enjin-promise ..enjin..)

                                          b (promise)
                                          trackref (ref (future (deliver b ..before-result..)))

                                          a (promise)]
                                         ?form ))]
  (fact "start-or-noop should start a stopped task"
    @b => ..before-result..
    (start-or-noop enjin-promise trackref (fn [enjin] enjin => ..enjin.. (deliver a ..after-result..) (Thread/sleep 50))) => :running
    (deref a 100 ..fail..) => ..after-result..))

(with-state-changes [(around :facts (let [trackref (ref nil)]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a never started task"
    (stop-or-noop trackref) => :none))

(with-state-changes [(around :facts (let [trackref (ref (future (Thread/sleep 100)))]
                                      ?form ))]
  (fact "stop-or-noop should stop a running task"
    (stop-or-noop trackref) => :stopped))

(with-state-changes [(around :facts (let [trackref (ref (future ..result..))]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a stopped task"
    (stop-or-noop trackref) => :stopped))

(with-state-changes [(around :facts (let [trackref (ref (future (Thread/sleep 100) ..result..))]
                                         ?form ))]
  (fact "join-or-noop should deref an existing task's future"
    (join-or-noop trackref) => :stopped))

(with-state-changes [(around :facts (let [trackref (ref nil)]
                                         ?form ))]
  (fact "join-or-noop should do nothing to a never-started task"
    (join-or-noop trackref) => :none))
