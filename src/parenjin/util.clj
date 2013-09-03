(ns parenjin.util
  (:use clojure.core.strint)
  (:require [clojure.set :as set])
  )

(defn test-val
  "check a single value against a specification. spec may be one of :
   - nil : value is optional : may take any value including nil
   - a function : will be called with the value. return truthy to accept
   - Class : value must be an instance of the Class
   - collection of specs : value must match one of the specs
   - e.g. [nil String] specifies an optional String"
  [spec val]
  (cond (= true spec) true
        (nil? spec) (nil? val)
        (fn? spec) (or (spec val) false)
        (class? spec) (instance? spec val)
        (sequential? spec) (or (some (fn [s] (test-val s val))
                                     spec)
                               false)))

(defn check-val
  "test a value against a spec, and throw an Exception if it fails"
  [key spec val]
  (if (test-val spec val)
    true
    (throw (RuntimeException. (<< "value ~{val} for key: ~{key} fails check")))))


(defn check-map
  "check a map against a specification. keyspecs contains entries
  {key spec} where spec may be any of the specs supported by check-val

  unknown keys in the supplied map will cause an exception, and
  unmet requirements will also cause an exception"
  [keyspecs m]
  (let [ks (keys keyspecs)
        unknown-keys (set/difference (set (keys m))
                                     (set ks))]
    (if (not-empty unknown-keys)
      (throw (RuntimeException. (<< "unknown keys: ~{unknown-keys}"))))

    (reduce (fn [r k] (and r (check-val k (keyspecs k) (m k))))
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
