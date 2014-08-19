(ns parenjin.application-proxy)

(defprotocol ApplicationProxyProtocol
  "an ApplicationProxy holds an application specification, and can create and destroy
   the application repeatedly, while a reference can be held to the ApplicationProxy"
  (create [this])
  (destroy [this])
  (create-webservice [this] [this devmode]
    "create a webservice compojure route wrapping all the required routes from the apps enjin's
     - devmode if truthy then creates a development-mode webservice, which will recreate the wrapped
       routes on every request. if falsey then creates a production-mode webservice"))
