(def shared
  '[
    [org.clojure/tools.logging "0.2.6"]
    [org.clojure/core.incubator "0.1.3"]

    [potemkin "0.3.3"]

    [compojure "1.1.6"]
    ])


(defproject parenjin "0.4.4"
  :description "parenjin : Parameterisable Application Engines for Compojure"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"

  :url "http://github.com/mccraigmccraig/parenjin"
  :scm {:name "git"
        :url "http://github.com/mccraigmccraig/parenjin"}

  :jvm-opts ["-Xmx128m"
             "-server"
             "-XX:MaxPermSize=128m"
             "-XX:+CMSClassUnloadingEnabled"
             "-XX:+UseConcMarkSweepGC"]

  :repl-options {:nrepl-middleware []}

  :plugins [[lein-midje "3.1.3"]]

  :dependencies ~(conj shared '[org.clojure/clojure "1.6.0"])

  :aliases {"with-all-dev" ["with-profile" "1.4,dev:1.5,dev:1.6,dev"]}
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}
             :production {}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  )
