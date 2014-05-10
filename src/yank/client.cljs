(ns yank.client
    (:require [ajax.core :refer [GET POST]]
              [clojure.browser.repl :as repl]
              [enfocus.core :as ef]
              [enfocus.effects :as effects]
              [enfocus.events :as events]
              [enfocus.bind :as bind]
              [fresnel.lenses :refer [Lens]])
    (:require-macros [enfocus.macros :as em])
    (:use-macros [enfocus.macros :only [deftemplate defsnippet defaction]]))

;;************************************************
;; Dev stuff
;;************************************************
(defn log [arg]
  (.log js/console arg))

;;************************************************
;; State
;;************************************************
(def initial-state
  {:code ""
   :state :initial ; :checked :broken :loading
   :advice nil
   :active-line nil
   :timer 0})

(def app (atom initial-state
               :validator (fn [{:keys [state advice]}]
                            (or (#{:checked :loading} state)
                                (nil? advice)))))
(def tick-interval 200)
(def code-mirror nil)

;;************************************************
;; Utils
;;************************************************

(defn sub-map-lens [kys]
  (reify Lens
    (-fetch [this value] (select-keys value kys))
    (-putback [this value subvalue] (merge value subvalue))))

(defn swapp! [& args]
  (swap! app #(merge % (apply hash-map args))))

(defn by-class [class]
  (first (.getElementsByClassName js/document class)))

(defn add-class-when [cond class]
  (if cond
    (ef/add-class class)
    (ef/remove-class class)))

(defn add-line-class [line target class]
  (.addLineClass code-mirror line (str target) class))

(defn remove-line-class [line target class]
  (.removeLineClass code-mirror line (str target) class))

(def example "(if (some test)\n  (some action)\n  nil)")
(defn show-example []
  (swapp! :code example))

;;************************************************
;; snippets and templates
;;************************************************

(defsnippet advice-snip "index.html" ".advice" [{:keys [expr alt line]} advice]
  ".line-link" (ef/do->
                (ef/content (str "line " line))
                (events/listen :click #(swapp! :active-line (dec line))))
  ".alt" (ef/content (str "  " alt))
  ".expr" (ef/content (str "  " expr)))

(defn render-result [node {:keys [state advice]}]
  (ef/at node
         ".advice"
         (ef/content
          (cond
           (= state :initial) ""
           (= state :broken) "Failed to check the code =(\nAre parenthesis balanced?"
           (empty? advice) "Looks OK to me"
           :default (map advice-snip advice)))
         ".result" (ef/do->
                    (add-class-when (not= state :loading) "text-info")
                    (add-class-when (= state :initial) "hidden"))))

(defn render-loader [node {:keys [state timer]}]
  (let [steps (mapv #(clojure.string/replace % " " "&nbsp;")
                   ["..." " .." "  ." "   " ".  " ".. "])
        dots (nth steps (mod timer (count steps)))]
    (ef/at node
           ".loader" (ef/do->
                      (ef/content (str "Loading" dots))
                      (add-class-when (not= state :loading) "invisible")))))

(defn render-about [node state]
  (ef/at node
         ".about" (add-class-when (not= state :initial) "hidden")))

;;************************************************
;; actions/navigation
;;************************************************

(defaction home []
  ".result-wrapper" (bind/bind-view app render-result
                                    (sub-map-lens [:state :advice]))
  ".loader-wrapper" (bind/bind-view app render-loader
                                    (sub-map-lens [:state :timer]))
  ".about-wrapper" (bind/bind-view app render-about [:state])
  ".example-link" (events/listen :click (fn [] (show-example) false))
  ".brand-link" (events/listen :click (fn [] (reset! app initial-state) false)))

(defn activate-editor []
  (let [ta (by-class "bin")
        update-code (fn [_ _] (swapp! :code (.getValue code-mirror)))]
    (set! code-mirror (.fromTextArea js/CodeMirror ta #js {:mode "clojure"
                                                           :lineNumbers true
                                                           :autofocus true}))
    (.on code-mirror "change" update-code)))

(defn start-timer []
  (let [inc-timer (fn [] (swap! app #(update-in % [:timer] inc)))]
    (js/setInterval inc-timer tick-interval)))

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
         :error-handler handle-error}))

(def try-create-bin-deb (.debounce js/_ try-create-bin 300))

;;************************************************
;; watchers
;;************************************************

(defn code-change [old new]
  (swapp! :active-line nil)
  (try-create-bin-deb new)
  (when (not= new (.getValue code-mirror))
    (.setValue code-mirror new)))

(defn advice-change [old new]
  (doseq [{line :line} old]
    (remove-line-class (dec line) :text "line-advice"))
  (doseq [{line :line} new]
    (add-line-class (dec line) :text "line-advice")))

(defn active-line-change [old new]
  (when old
    (remove-line-class old :background "line-active"))
  (when new
    (add-line-class new :background "line-active")
    (.. code-mirror (getDoc) (setCursor new))))

(def handlers [[:code code-change]
               [:advice advice-change]
               [:active-line active-line-change]])

(defn watcher [key ref old new]
  (doseq [[key handler] handlers
          :let [o (key old)
                n (key new)]]
    (when (not= o n) (handler o n))))
(add-watch app :watch-change watcher)

;;************************************************
;; onload
;;************************************************

(set! (.-onload js/window)
      #(do
         (home)
         (activate-editor)
         (start-timer)))
