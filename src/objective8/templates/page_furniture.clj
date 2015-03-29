(ns objective8.templates.page-furniture
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as string]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [objective8.utils :as utils]))

(def library-html "templates/jade/library.html")
(def library-html-resource (html/html-resource library-html))

(defn translator
  "Returns a translation function which replaces the
   content of nodes with translations for k"
  [{:keys [translations] :as context}]
  (fn [k] 
    #(assoc % :content (translations k))))

(defn text->p-nodes
  "Turns text into a collection of paragraph nodes based on linebreaks.
   Returns nil if no text is supplied"
  [text]
  (when text
    (let [newline-followed-by-optional-whitespace #"(\n+|\r+)\s*"]
    (map (fn [p] (html/html [:p p])) (clojure.string/split text
                                                           newline-followed-by-optional-whitespace)))))

(defn- node->l8n-class-names [node]
  (re-seq #"l8n--\S+" (get-in node [:attrs :class])))

(defn- apply-translation [node translation l8n-opts]
  (if translation
    (case (first l8n-opts)
      "html" (assoc node :content (html/html-snippet translation))
      "attr" (assoc-in node [:attrs (keyword (second l8n-opts))] translation) 
      nil (assoc node :content translation))
    (do (prn node)
        node)))

(defn- apply-translations [l8n-classes node translations]
  (let [[l8n-class & more] l8n-classes
        [_ l8n-namespace l8n-key & l8n-opts] (string/split l8n-class #"--") 
        translation-key (keyword l8n-namespace l8n-key) 
        translation (translations translation-key)]
    (if more
      (apply-translation (apply-translations more node translations) translation l8n-opts)
      (apply-translation node translation l8n-opts))))

(defn- translate-node [node {:keys [translations] :as context}]
  (let [l8n-classes (node->l8n-class-names node)] 
    (apply-translations l8n-classes node translations)))

(defn translate [context nodes]
  (html/at nodes
    [(html/attr-contains :class "l8n--")] #(translate-node % context)))

;; MASTHEAD

(def masthead-snippet (html/select library-html-resource [:.clj-masthead-signed-out])) 
(def masthead-signed-in-snippet (html/select library-html-resource [:.clj-masthead-signed-in]))

(defn masthead [{{uri :uri} :ring-request :keys [user] :as context}]
  (html/at masthead-snippet
           [:.clj-masthead-signed-out] (if user
                                         (html/substitute masthead-signed-in-snippet)
                                         identity)
           [:.clj-masthead-sign-in] (html/set-attr "href" (str "/sign-in?refer=" uri))
           [:.clj-username] (html/content (:username user))))

;; STATUS BAR

(html/defsnippet flash-bar library-html [:.clj-flash-message-bar] [flash]
  [:.clj-flash-message-bar-text] (html/content flash))

(html/defsnippet status-flash-bar
  library-html [:.clj-status-bar] [{:keys [doc translations] :as context}]
  [:.clj-status-bar] (if-let [flash (:flash doc)] 
                       (html/substitute (flash-bar flash))
                       identity))


;; DRAFTING HAS STARTED MESSAGE

(html/defsnippet drafting-message library-html [:.clj-drafting-message] [{{objective :objective} :data
                                                                          translations :translations
                                                                          :as context}]
  [html/any-node] (when (:drafting-started objective) identity)
  [:.clj-drafting-message-title] (html/content (translations :notifications/drafting-message-title))
  [:.clj-drafting-message-body] (html/content (translations :notifications/drafting-message-body))
  [:.clj-drafting-message-link] (html/do->
                                  (html/set-attr "href" (str "/objectives/" (:_id objective) "/drafts"))
                                  (html/content (translations :notifications/drafting-message-link))))

;; WRITER LIST


(html/defsnippet invite-writer-form
  library-html [:.clj-invite-a-writer-form] [{:keys [translations data]}]
  [:.clj-invite-a-writer-form] (html/prepend (html/html-snippet (anti-forgery-field)))
  [:.l8n-label-writer-name] (html/content (translations :invite-writer/writer-name-label))
  [:.l8n-input-writer-name] (html/set-attr "title" (translations :invite-writer/writer-name-attr-title))
  [:.l8n-label-writer-email] (html/content (translations :invite-writer/writer-email-label))
  [:.l8n-input-writer-email] (html/set-attr "title" (translations :invite-writer/writer-email-attr-title))
  [:.l8n-label-writer-reason] (html/content (translations :invite-writer/writer-reason-label))
  [:.l8n-input-writer-reason] (html/set-attr "title" (translations :invite-writer/writer-reason-attr-title))
  [:.l8n-button-invite-writer] (html/content (translations :invite-writer/invite-button)))

(html/defsnippet sign-in-to-invite-writer
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request]}]
  [:.l8n-before-link] (html/content (translations :invite-writer/sign-in-please))
  [:.l8n-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request)))
                         (html/content (translations :invite-writer/sign-in)))
  [:.l8n-after-link] (html/content (translations :invite-writer/sign-in-to)))

(defn invite-writer [{user :user :as context}]
  (if user
    (invite-writer-form context)
    (sign-in-to-invite-writer context)))

(html/defsnippet empty-writer-list-item
  library-html [:.clj-empty-writer-list-item] [{translations :translations}]
  [:.clj-empty-writer-list-item] (html/content (translations :candidate-list/no-candidates)))

(html/defsnippet writer-list-items
  library-html [:.clj-writer-item-without-photo] [candidates]
  [:.clj-writer-item-without-photo :a] nil
  [:.clj-writer-item-without-photo] (html/clone-for [candidate candidates]
                                                    [:.clj-writer-name] (html/content (:writer-name candidate))
                                                    [:.clj-writer-description] (html/content (:invitation-reason candidate))))

(defn writer-list [context]
  (let [candidates (get-in context [:data :candidates])]
    (if (empty? candidates)
      (empty-writer-list-item context)
      (writer-list-items candidates))))
;; ANSWER LIST

(html/defsnippet sign-in-to-add-answer
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request] :as context}]
  [:.l8n-before-link] (html/content (translations :answer-sign-in/please))
  [:.l8n-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request)))
                         (html/content (translations :answer-sign-in/sign-in)))
  [:.l8n-after-link] (html/content (translations :answer-sign-in/to)))

;; QUESTION LIST

(html/defsnippet empty-question-list-item
  library-html [:.clj-empty-question-list-item] [{translations :translations}]
  [:.clj-empty-question-list-item] (html/content (translations :question-list/no-questions)))

(html/defsnippet question-list-items
  library-html [:.clj-question-item] [questions translations]
  [:.clj-question-item] (html/clone-for [question questions]
                                        [:.clj-question-text] (html/content (:question question))
                                        [:.clj-answer-link] (html/do->
                                                              (html/content (translations :objective-view/answer-link))
                                                              (html/set-attr "href" (str "/objectives/" (:objective-id question)
                                                                                         "/questions/" (:_id question))))))

(defn question-list [{translations :translations :as context}]
  (let [questions (get-in context [:data :questions])]
    (if (empty? questions)
      (empty-question-list-item context)
      (question-list-items questions translations))))

(html/defsnippet add-question-form
  library-html [:.clj-question-create-form] [{:keys [translations data]}]
  [:.clj-question-create-form] (html/prepend (html/html-snippet (anti-forgery-field)))

  [:.l8n-label-add-question] (html/content (translations :question-create/question-label))
  [:.l8n-textarea-add-question] (html/set-attr "title" (translations :question-create/question-title))
  [:.l8n-button-add-question] (html/content (translations :question-create/post-button)))

(html/defsnippet sign-in-to-add-question
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request]}]
  [:.l8n-before-link] (html/content (translations :question-sign-in/please))
  [:.l8n-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request)))
                         (html/content (translations :question-sign-in/sign-in)))
  [:.l8n-after-link] (html/content (translations :question-sign-in/to)))

(defn add-question [{user :user :as context}]
  (if user
    (add-question-form context)
    (sign-in-to-add-question context)))

;; COMMENT LIST

(html/defsnippet empty-comment-list-item
  library-html [:.clj-empty-comment-list-item] [translations]
  [:.clj-empty-comment-list-item] (html/content (translations :comment-view/no-comments)))

(html/defsnippet comment-list-items
  library-html [:.clj-comment-item] [comments]
  [:.clj-comment-item] (html/clone-for [comment comments]
                                       [:.clj-comment-author] (html/content (:username comment))
                                       [:.clj-comment-date] (html/content (utils/iso-time-string->pretty-time (:_created_at comment)))
                                       [:.clj-comment-text] (html/content (:comment comment))
                                       [:.clj-comment-actions] nil))

(defn comment-list [{translations :translations :as context}]
  (let [comments (get-in context [:data :comments])]
    (if (empty? comments)
      (empty-comment-list-item translations)
      (comment-list-items comments))))

;; COMMENT CREATE

(html/defsnippet comment-create-form
  library-html [:.clj-add-comment-form] [{:keys [translations data ring-request]} comment-target]
  [:.clj-add-comment-form] (html/prepend (html/html-snippet (anti-forgery-field)))
  [:.clj-refer] (html/set-attr "value" (:uri ring-request))
  [:.clj-comment-on-uri] (html/set-attr "value" (get-in data [comment-target :uri]))
  [:.clj-add-comment] (html/content (translations :comment-create/post-button)))

(html/defsnippet sign-in-to-comment
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request]}]
  [:.clj-before-link] (html/content (translations :comment-sign-in/please))
  [:.clj-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request) "%23comments"))
                         (html/content (translations :comment-sign-in/sign-in)))
  [:.clj-after-link] (html/content (translations :comment-sign-in/to)))

(defn comment-create [{user :user :as context} comment-target]
  (if user
    (comment-create-form context comment-target)
    (sign-in-to-comment context)))
