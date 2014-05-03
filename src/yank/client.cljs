(ns yank.client
    (:require [enfocus.core :as ef]
              [enfocus.effects :as effects]
              [enfocus.events :as events]
              [enfocus.bind :as bind]
              [ajax.core :refer [GET POST]]
              [clojure.browser.repl :as repl])
    (:require-macros [enfocus.macros :as em])
    (:use-macros [enfocus.macros :only [deftemplate defsnippet defaction]]))

;;************************************************
;; Dev stuff
;;************************************************
(defn log [arg]
  (.log js/console arg))

(def dev-mode true)

(defn repl-connect []
  (when dev-mode
    (repl/connect "http://localhost:9000/repl")))

;;************************************************
;; State
;;************************************************

(def app (atom {:code ""
                :state :initial ; :checked :broken :loading
                :advice nil
                :active-line nil}
               :validator (fn [{:keys [state advice]}]
                            (or (#{:checked :loading} state)
                                (nil? advice)))))

(def timer (atom 0))
(def tick-interval 200)

(def code-mirror nil)

;;************************************************
;; Utils
;;************************************************
(defn start-timer []
  (let [inc-timer #(swap! timer inc)]
    (js/setInterval inc-timer tick-interval)))

(defn add-line-class [line class]
  (.addLineClass code-mirror line "text" class))

(defn remove-line-class [line class]
  (.removeLineClass code-mirror line "text" class))

(defn swapp! [& args]
  (swap! app #(merge % (apply hash-map args))))

(defn format-one [{:keys [expr alt line]}]
  (str "line " line ": consider using\n" (str "  " alt)
       "\ninstead of\n" (str "  " expr)))

(defn format-advice [state advice]
  (cond
   (= state :initial) "Paste some clojure code"
   (= state :broken) "Failed to check the code =(\nAre parenthesis balanced?"
   (empty? advice) "Looks OK to me"
   :default (clojure.string/join "\n\n" (map format-one advice))))

;;************************************************
;; Retrieving data from dom
;;************************************************

(defn by-class [class]
  (first (.getElementsByClassName js/document class)))

;;************************************************
;; snippets and templates
;;************************************************

(defsnippet home-snip "index.html" "#stage" [])
(defsnippet advice-snip "index.html" ".advice" [{:keys [expr alt line]} advice]
  ".line-link" (ef/do->
                (ef/content (str "line " line ":"))
                (events/listen :click #(swapp! :active-line (dec line))))
  ".alt" (ef/content (str "  " alt))
  ".expr" (ef/content (str "  " expr)))

(defn render-result [node {:keys [state advice]}]
  (let [transform (if (= state :loading) ef/remove-class ef/add-class)]
  (ef/at node
         ".advice"
         (ef/content
          (cond
           (= state :initial) "Paste some clojure code"
           (= state :broken) "Failed to check the code =(\nAre parenthesis balanced?"
           (empty? advice) "Looks OK to me"
           :default (map advice-snip advice)))

         ".result" (transform "text-info")
         ".loader" (transform "invisible"))))

(defn render-loading [node time]
  (let [steps (mapv #(clojure.string/replace % " " "&nbsp;")
                   ["..." " .." "  ." "   " ".  " ".. "])
        dots (nth steps (mod time (count steps)))]
    (ef/at node
           ".loader" (ef/content (str "Loading" dots)))))

;;************************************************
;; actions/navigation
;;************************************************

(defaction home []
  ".result-wrapper" (bind/bind-view timer render-loading)
  ".result-wrapper" (bind/bind-view app render-result))

(defn activate-editor []
  (let [ta (by-class "bin")
        update-code (fn [_ _] (swapp! :code (.getValue code-mirror)))]
    (set! code-mirror (.fromTextArea js/CodeMirror ta #js {:mode "clojure"
                                                           :lineNumbers true
                                                           :autofocus true}))
    (.on code-mirror "change" update-code)))

;;************************************************
;; talking to server
;;************************************************

(defn handle-error [err]
  (log (str err))
  (swapp! :state :broken :adivce nil))

(defn try-create-bin [code]
  (swapp! :state :loading)
  (POST "/bin"
        {:params {:code code}
         :handler (fn [advice]
                    (let [state (if advice :checked :broken)]
                      (swapp! :state state :advice advice)))
         ; TODO: real error handler
         :error-handler handle-error}))

(def try-create-bin-deb (.debounce js/_ try-create-bin 300))

(defn code-change [old new]
  (swapp! :active-line nil)
  (try-create-bin-deb new))

(defn advice-change [old new]
  (doseq [{line :line} old]
    (remove-line-class (dec line) "line-advice"))
  (doseq [{line :line} new]
    (add-line-class (dec line) "line-advice")))

(defn active-line-change [old new]
  (when old
    (remove-line-class old "line-active"))
  (when new
    (add-line-class new "line-active")
    (.. code-mirror (getDoc) (setCursor new))))

(def handlers [[:code code-change]
               [:advice advice-change]
               [:active-line active-line-change]])

(defn wather [key ref old new]
  (doseq [[key handler] handlers
          :let [o (key old)
                n (key new)]]
    (when (not= o n) (handler o n))))
(add-watch app :watch-change wather)

;;************************************************
;; onload
;;************************************************

(set! (.-onload js/window)
      #(do
         (home)
         (activate-editor)
         (start-timer)))
