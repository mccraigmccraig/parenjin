(ns parenjin.application-proxy)

(defprotocol ApplicationProxy
  (create [this])
  (start [this])
  (destroy [this]))
