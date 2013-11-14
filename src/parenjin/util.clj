(ns parenjin.util
  (:use clojure.core.strint)
  (:require [clojure.set :as set])
  )

(defn derefable?
  "true if obj is an instance of clojure.lang.IDeref"
  [obj]
  (instance? clojure.lang.IDeref obj))

(defn test-val
  "check a single value against a specification. spec may be one of :
   - nil : value is optional : may take any value including nil
   - a function : will be called with the value. return truthy to accept
   - Class : value must be an instance of the Class
   - collection of specs : value must match one of the specs
   - e.g. [nil String] specifies an optional String"
  [spec val & {:keys [skip-ideref?]}]
  (cond (= true spec) true
        (and skip-ideref? (derefable? val)) true
        (nil? spec) (nil? val)
        (fn? spec) (or (spec val) false)
        (class? spec) (instance? spec val)
        (sequential? spec) (or (some (fn [s] (test-val s val))
                                     spec)
                               false)))

(defn check-val
  "test a value against a spec, and throw an Exception if it fails"
  [key spec val & {:keys [skip-ideref?]}]
  (if (test-val spec val :skip-ideref? skip-ideref?)
    true
    (throw (RuntimeException. (<< "value <~{val}> for key: ~{key} fails check")))))


(defn check-map
  "check a map against a specification. keyspecs contains entries
  {key spec} where spec may be any of the specs supported by check-val

  unknown keys in the supplied map will cause an exception, and
  unmet requirements will also cause an exception"
  [keyspecs m & {:keys [skip-ideref?]}]
  (let [ks (keys keyspecs)
        unknown-keys (set/difference (set (keys m))
                                     (set ks))]
    (if (not-empty unknown-keys)
      (throw (RuntimeException. (<< "unknown keys: ~{unknown-keys}"))))

    (reduce (fn [r k] (and r (check-val k (keyspecs k) (m k) :skip-ideref? skip-ideref?)))
            true
            ks)))

(defn resolve-ref
  "resolve a reference which may be
   - a symbol : the namespace part is required and the symbol resolved
                and dereferences
   - a keyword : converted to a symbol and resolved
   - a var : the var is dereferenced
   - something else : returned unchanged"
  [n]
  (cond (symbol? n) (do (require (symbol (namespace n)))
                        (deref (resolve n)))
        (keyword? n) (do (require (symbol (namespace n)))
                         (deref (ns-resolve (symbol (namespace n))
                                            (symbol (name n)))))
        (var? n) (deref n)
        true n))

(defn resolve-obj
  "resolve an object from it's spec which may be either a reference which is resolved with resolve-ref,
   or a function which is called to return the object"
  [spec]
  (let [spec-value (resolve-ref spec)]
    (if (fn? spec-value)
      (spec-value)
      spec-value)))

(defn deref-if-pending
  "dereference an object if it's an IPending, letting circular dependencies
   between enjins be expressed as Delays to other enjins"
  [obj]
  (if (instance? clojure.lang.IPending obj)
    (deref obj)
    obj))

(defn deref-if-deref
  "dereference an object if it's an IDeref, otherwise return it unchanged"
  [obj]
  (if (instance? clojure.lang.IDeref obj)
    (deref obj)
    obj))

(defn future-status
  "get the status of a future, returning :running, :stopped or :none"
  [f]
  (cond (and f (not (realized? f))) :running
        f :stopped
        true :none))

(defn start-or-noop
  "if it's not running call (defs id) in a future and retain the future in <trackref>"
  [enjin trackref defs id]
  (dosync
   (let [f ((ensure trackref) id)]
     (if (or (not f) (realized? f))
       (ref-set trackref
                (assoc @trackref id
                       (future ((defs id) enjin)))))))
  (future-status (@trackref id)))

(defn stop-or-noop
  "if it is running, stop the future in (@trackref id)"
  [enjin trackref id]
  (dosync
   (if-let [f ((ensure trackref) id)]
     (future-cancel f)))
  (future-status (@trackref id)))

(defn merge-check-disjoint
  "merge two maps, throwing an exception if their keysets are not disjoint"
  [ma mb & [msg]]
  (let [shared-keys (set/intersection (-> ma keys set) (-> mb keys set))]
    (if (empty? shared-keys)
      (merge ma mb)
      (throw (RuntimeException. (str (or msg "key clash: ") shared-keys))))))
