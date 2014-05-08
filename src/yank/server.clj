(ns yank.server
  (:require [compojure.route :as route]
            [ring.util.response :as resp])
  (:use compojure.core
        compojure.handler
        ring.middleware.edn
        ring.middleware.logger
        [kibit.check :only [check-reader]]))

(defn check [code]
  (try (-> code
           java.io.StringReader.
           check-reader
           vec)
       (catch Throwable e
         nil)))

(defn response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn new-bin [code]
  (response (check code)))

(defroutes compojure-handler
  (GET "/" [] (resp/file-response "index.html" {:root "public"}))
  (POST "/bin" [code] (new-bin code))
  (route/resources "/")
  (route/not-found "Not found!"))

;setting up a simple resource handler for ring
(def app (-> compojure-handler
             site
             wrap-edn-params
             wrap-with-plaintext-logger))
