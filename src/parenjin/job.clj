(ns parenjin.job
  (:use clojure.core.incubator
        midje.sweet
        midje.open-protocols)
  (:require [parenjin.util :as util]))

(defprotocol Job
  (start [this])
  (stop [this])
  (status [this])
  (join [this]))

(defn- start-or-noop
  "if it's not running call (defs id) in a future and retain the future in <trackref>"
  [enjin track-ref job-fn]
  (dosync
   (let [f (ensure track-ref)]
     (if (or (not f) (realized? f))
       (ref-set track-ref
                (future (job-fn enjin))))))
  (util/future-status @track-ref))

(defn- stop-or-noop
  "if it is running, stop the future in (@trackref id)"
  [track-ref]
  (dosync
   (if-let [f (ensure track-ref)]
     (future-cancel f)))
  (util/future-status @track-ref))

(defn- join-or-noop
  "do a blocking deref of a job future, if it's running"
  [track-ref]
  (let [f (-?> track-ref deref deref)]
    (util/future-status @track-ref)))

(defrecord job [enjin-promise* track-ref* job-fn*]
    Job
  (start [this] (start-or-noop enjin-promise* track-ref* job-fn*))
  (stop [this] (stop-or-noop track-ref*))
  (status [this] (util/future-status @track-ref*))
  (join [this] (join-or-noop track-ref*)))

(defn create-job
  [enjin job-fn]
  (map->job {:enjin-promise* enjin (ref nil) job-fn}))

(defn run-jobs-parallel
  "kick off all jobs in parallel. returns immediately"
  [& jobs]
  (->> jobs
       flatten
       (map (fn [job] (start job)))
       doall))

(defn join-jobs
  "block for the completion of all jobs"
  [& jobs]
  (->> jobs
       flatten
       (map (fn [job] (join job)))
       doall))

(defn run-jobs-serial
  "run all jobs one after another. returns after the last job completes"
  [& jobs]
  (->> jobs
       flatten
       (map (fn [job] (start job) (join job)))
       doall))
