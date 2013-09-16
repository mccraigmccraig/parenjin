(ns parenjin.application-proxy)

(defprotocol ApplicationProxy
  "an ApplicationProxy holds an application specification, and can create and destroy
   the application repeatedly, while a reference can be held to the ApplicationProxy"
  (create [this])
  (destroy [this]))
