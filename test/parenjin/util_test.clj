(ns parenjin.util-test
  (:use midje.sweet
        parenjin.util)
  (:import [java.util.concurrent CancellationException]))

(fact "test-val should test a value against a spec returning true or false"
  (test-val true nil) => true
  (test-val true ..foo..) => true

  (test-val nil nil) => true
  (test-val nil ..foo..) => false

  (test-val identity nil) => false
  (test-val sequential? []) => true
  (test-val sequential? {}) => false

  (test-val String "foo") => true
  (test-val String nil) => false
  (test-val String 1) => false

  (test-val [nil String] nil) => true
  (test-val [nil String] "foo") => true
  (test-val [nil String] 1) => false


  (test-val String (atom nil)) => false
  (test-val String (atom nil) :skip-ideref? true) => true)

(fact "check-val should test a value and throw an exception if it fails"
  (check-val :foo Number 1) => true
  (check-val :foo Number "foo") => (throws RuntimeException)

  (check-val :foo String (atom nil)) => (throws RuntimeException)
  (check-val :foo String (atom nil) :skip-ideref? true) => true)

(fact "check-map should check a map against a map of specs"
  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar nil}) => true
  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar "bar"}) => true
  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar 10}) => (throws RuntimeException)

  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar "bar" :baz 100}) => (throws RuntimeException)
  (check-map {:foo Number :bar [String nil]} {:bar "bar"}) => (throws RuntimeException)

  (check-map {:bar String} {:bar (atom nil)}) => (throws RuntimeException)
  (check-map {:bar String} {:bar (atom nil)} :skip-ideref? true) => true
  )

(fact "resolve-ref should resolve a symbol, keyword, var or literal"
  (resolve-ref 'clojure.core/identity) => identity
  (resolve-ref :clojure.core/identity) => identity
  (resolve-ref #'clojure.core/identity) => identity
  (resolve-ref ..foo..) => ..foo..)

(fact "resolve-obj should call a function if the resolved ref is a function, otherwise just resolve the ref"
  (resolve-obj ..foo..) => ..result..
  (provided
    (resolve-ref ..foo..) => (fn [] ..result..))

  (resolve-obj ..foo..) => ..result..
  (provided
    (resolve-ref ..foo..) => ..result..))

(fact "deref-if-pending should deref an object if it is pending"
  (deref-if-pending (delay ..val..)) => ..val..
  (deref-if-pending ..val..) => ..val..)

(fact "derefable? should test whether an object is derefable"
  (derefable? (atom ..vall..)) => true
  (derefable? 100) => false)

(fact "deref-if-deref should deref an object if it is dereffable"
  (deref-if-deref (atom ..val..)) => ..val..
  (deref-if-deref ..val..) => ..val..)

(fact "future-status should return a status for a future"
  (future-status nil) => :none

  (let [f (future 1)]
    (Thread/sleep 100)
    (future-status f) => :stopped)

  (let [f (future (do (Thread/sleep 100) 1))]
    (future-status f) => :running))

(fact "merge-check-disjoint should merge two maps with disjoint keys"
  (merge-check-disjoint {:foo 10} {:bar 20}) => {:foo 10 :bar 20})

(fact "merge-check-disjoint should bork if map keys are not disjoint sets"
  (merge-check-disjoint {:foo 10} {:foo 20}) => (throws #"key clash.*:foo"))

(fact "merge-check-disjoint should merge if keysets are not disjoint but values
       related to intersecting keys are identical"
  (merge-check-disjoint {:foo 10 :bar 20} {:foo 10 :baz 30}) => {:foo 10 :bar 20 :baz 30})

(fact "with-ex-info* should decorate a exceptions with supplied info"
  (try
    (with-ex-info* "foo" {:foo ..foo-data..}
      (fn []
        (with-ex-info* "bar" {:bar ..bar-data..}
          (fn []
            (throw (RuntimeException. "boo"))))))
    (catch Exception e
      [(.getMessage e) (ex-data e)])) => ["foo" {:foo ..foo-data.. :bar ..bar-data..}])

(fact "with-ex-info* should return the result of the function if no exception is thrown"
  (with-ex-info* "foo" {:foo ..foo-data..} (fn [] ..result..)) => ..result..)

(fact "with-ex-info should decorate exceptions"
  (try
    (with-ex-info "foo" {:foo ..foo-data..}
      (with-ex-info "bar" {:bar ..bar-data..}
        (throw (RuntimeException. "boo"))))
    (catch Exception e
      [(.getMessage e) (ex-data e)])) => ["foo" {:foo ..foo-data.. :bar ..bar-data..}])
