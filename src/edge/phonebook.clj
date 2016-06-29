;; Copyright © 2016, JUXT LTD.

(ns edge.phonebook
  (:require
   [edge.phonebook.db :as db]
   [clojure.tools.logging :refer :all]
   [edge.phonebook.html :as html]
   [hiccup.core :refer [html]]
   [selmer.parser :as selmer]
   [schema.core :as s]
   [yada.yada :as yada]))

(defn new-index-resource [db]
  (yada/resource
   {:id :edge.resources/phonebook-index
    :description "Phonebook entries"
    :produces [{:media-type
                #{"text/html" "application/edn;q=0.9" "application/json;q=0.8"}
                :charset "UTF-8"}]
    :methods
    {:get {:parameters {:query {(s/optional-key :q) String}}
           :swagger/tags ["default" "getters"]
           :response (fn [ctx]
                       (let [q (get-in ctx [:parameters :query :q])
                             entries (if q
                                       (db/search-entries db q)
                                       (db/get-entries db))]
                         (case (yada/content-type ctx)
                           "text/html" (html/index-html ctx entries q)
                           entries)))}

     :post {:parameters {:form {:surname String :firstname String :phone String}}
            :consumes #{"application/x-www-form-urlencoded"}
            :response (fn [ctx]
                        (let [id (db/add-entry db (get-in ctx [:parameters :form]))]
                          (java.net.URI. (:uri (yada/uri-for ctx :edge.resources/phonebook-entry {:route-params {:id id}})))))}}}))

(defn new-entry-resource [db]
  (yada/resource
   {:id :edge.resources/phonebook-entry
    :description "Phonebook entry"
    :parameters {:path {:id Long}}
    :produces [{:media-type #{"text/html"
                              "application/edn;q=0.9"
                              "application/json;q=0.8"}
                :charset "UTF-8"}]

    :methods
    {:get
     {:swagger/tags ["default" "getters"]
      :response
      (fn [ctx]
        (let [id (get-in ctx [:parameters :path :id])
              {:keys [firstname surname phone] :as entry} (db/get-entry db id)]
          (when entry
            (case (yada/content-type ctx)
              "text/html" (selmer/render-file
                           "phonebook-entry.html"
                           {:title "Edge phonebook"
                            :entry entry
                            :ctx ctx
                            :id id})

              entry))))}

     :put
     {:parameters
      {:form {:surname String
              :firstname String
              :phone String}}

      :consumes
      [{:media-type #{"multipart/form-data"
                      "application/x-www-form-urlencoded"}}]

      :response
      (fn [ctx]
        (let [entry (get-in ctx [:parameters :path :id])
              form (get-in ctx [:parameters :form])]
          (assert entry)
          (assert form)
          (db/update-entry db entry form)))}

     :delete
     {:produces "text/plain"
      :response
      (fn [ctx]
        (let [id (get-in ctx [:parameters :path :id])]
          (db/delete-entry db id)
          (let [msg (format "Entry %s has been removed" id)]
            (case (get-in ctx [:response :produces :media-type :name])
              "text/plain" (str msg "\n")
              "text/html" (html [:h2 msg])
              ;; We need to support JSON for the Swagger UI
              {:message msg}))))}}

    :responses {404 {:produces #{"text/html"}
                     :response (fn [ctx]
                                 (infof "parameters are '%s'" (:parameters ctx))
                                 (selmer/render-file
                                  "phonebook-404.html"
                                  {:title "No phonebook entry"
                                   :ctx ctx}))}}}))

(defn phonebook-routes [db]
  ["/phonebook"
   [
    ;; Phonebook index
    ["" (new-index-resource db)]

    ;; Phonebook entry, with path parameter
    [["/" :id] (new-entry-resource db)]
    ]])