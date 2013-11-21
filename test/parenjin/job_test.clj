(ns parenjin.job-test
  (:use midje.sweet
        parenjin.job)
  (:require [parenjin.util :as util]
            [parenjin.job :as job])
  (:import [java.util.concurrent CancellationException]))

(with-state-changes [(around :facts (let [result (promise)
                                          trackref (ref nil)]
                                         ?form ))]
  (fact "start-or-noop should start a never started task"
    (#'job/start-or-noop ..enjin.. trackref (fn [enjin] enjin => ..enjin.. (deliver result ..result..) (Thread/sleep 50))) => :running
    (deref result 0 ..fail..) => ..result..))

(with-state-changes [(around :facts (let [result (promise)
                                          trackref (ref (future (Thread/sleep 100) (deliver result ..result..)))]
                                         ?form ))]
  (fact "start-or-noop should do nothing to a running task"
    (#'job/start-or-noop ..enjin.. trackref (fn [enjin] (deliver result ..fail..))) => :running
    (deref result 100 ..nocall..) => ..result..))

(with-state-changes [(around :facts (let [b (promise)
                                          trackref (ref (future (deliver b ..before-result..)))

                                          a (promise)]
                                         ?form ))]
  (fact "start-or-noop should start a stopped task"
    @b => ..before-result..
    (#'job/start-or-noop ..enjin.. trackref (fn [enjin] enjin => ..enjin.. (deliver a ..after-result..) (Thread/sleep 50))) => :running
    (deref a 100 ..fail..) => ..after-result..))

(with-state-changes [(around :facts (let [trackref (ref nil)]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a never started task"
    (#'job/stop-or-noop trackref) => :none))

(with-state-changes [(around :facts (let [trackref (ref (future (Thread/sleep 100)))]
                                      ?form ))]
  (fact "stop-or-noop should stop a running task"
    (#'job/stop-or-noop trackref) => :stopped))

(with-state-changes [(around :facts (let [trackref (ref (future ..result..))]
                                         ?form ))]
  (fact "stop-or-noop should do nothing to a stopped task"
    (#'job/stop-or-noop trackref) => :stopped))

(with-state-changes [(around :facts (let [trackref (ref (future (Thread/sleep 100) ..result..))]
                                         ?form ))]
  (fact "join-or-noop should deref an existing task's future"
    (#'job/join-or-noop trackref) => :stopped))

(with-state-changes [(around :facts (let [trackref (ref nil)]
                                         ?form ))]
  (fact "join-or-noop should do nothing to a never-started task"
    (#'job/join-or-noop trackref) => :none))

(fact "create-job should create a job"
  (let [job (create-job ..enjin.. ..job-fn..)]
    (:enjin* job) => ..enjin..
    (:job-fn* job) => ..job-fn..
    (-> job :track-ref* type) => clojure.lang.Ref
    (-> job :track-ref* deref) => nil))

(fact "create-job should bork if enjin is not an enjin"
  (create-job nil (fn [] ..result..)) => (throws #"enjin arg is not an enjin"))

(fact "create-job should bork if job-fn is not a function"
  (create-job ..enjin.. nil) => (throws #"job-fn arg is not a fn"))

(fact "run-jobs-parallel should start all jobs in parallel"
  (run-jobs-parallel [..job1.. ..job2..]) => [..job1-status.. ..job2-status..]
  (provided
    (start ..job1..) => ..job1-status..
    (start ..job2..) => ..job2-status..))
(fact "join-jobs should block for completion of all jobs"
  (join-jobs [..job1.. ..job2..]) => [..job1-finished-status.. ..job2-finished-status..]
  (provided
    (join ..job1..) => ..job1-finished-status..
    (join ..job2..) => ..job2-finished-status..))

(fact "run-jobs-serial should run all jobs in sequence"
  (run-jobs-serial [..job1.. ..job2..]) => [..job1-finished-status.. ..job2-finished-status..]
  (provided
    (start ..job1..) => ..job1-status..
    (join ..job1..) => ..job1-finished-status..
    (start ..job2..) => ..job2-status..
    (join ..job2..) => ..job2-finished-status..))
