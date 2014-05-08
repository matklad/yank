(ns yank.server
  (:require [compojure.route :as route])
  (:use compojure.core
        compojure.handler
        carica.core
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

;handling routing "/" -> "/index.html"
(defn wrap-index [handler]
  (fn [req]
    (if (= (:uri req) "/")
      (handler (assoc req :uri "/index.html"))
      (handler req))))

(defn new-bin [code]
  (response (check code)))

(defroutes compojure-handler
  (POST "/bin" [code] (new-bin code))
  (route/resources "/")
  (route/files "/" {:root (config :external-resources)})
  (route/not-found "Not found!"))

;setting up a simple resource handler for ring
(def app (-> compojure-handler
             site
             wrap-edn-params
             wrap-with-plaintext-logger
             wrap-index))
