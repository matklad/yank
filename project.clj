(defproject yank "0.1.0-SNAPSHOT"
  :description "Clojure pastebin with kibit"
  :url "http://exampl.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [cljs-ajax "0.2.3"]
                 [ring/ring-core "1.1.8"]
                 [ring/ring-jetty-adapter "1.1.8"]
                 [fogus/ring-edn "0.2.0"]
                 [enfocus "2.1.0-SNAPSHOT"]
                 [jonase/kibit "0.0.9-SNAPSHOT"]
                 [ring.middleware.logger "0.4.0"]]
  :plugins [[lein-cljsbuild "0.3.2"]
            [lein-ring "0.8.3"]]
  :cljsbuild {:builds [{:source-paths ["src"],
                        :compiler {:output-to "resources/public/js/main.js"
                                   :externs ["resources/externs/underscore.js"
                                             "resources/externs/codemirror.js"]}}]}
  :ring {:handler yank.server/app})
