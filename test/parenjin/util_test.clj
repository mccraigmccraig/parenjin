(ns parenjin.util-test
  (:use midje.sweet
        parenjin.util))

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
  (test-val [nil String] 1) => false)

(fact "check-val should test a value and throw an exception if it fails"
  (check-val :foo Number 1) => true
  (check-val :foo Number "foo") => (throws RuntimeException))

(fact "check-map should check a map against a map of specs"
  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar nil}) => true
  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar "bar"}) => true
  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar 10}) => (throws RuntimeException)

  (check-map {:foo Number :bar [String nil]} {:foo 10 :bar "bar" :baz 100}) => (throws RuntimeException)
  (check-map {:foo Number :bar [String nil]} {:bar "bar"}) => (throws RuntimeException))

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
