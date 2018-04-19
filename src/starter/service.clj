(ns starter.service
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [clojure.java.jdbc :as jdbc]
            [hikari-cp.core :as hikari]
            [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.keyword-params :as ring.keyword-params]
            [ring.middleware.params :as ring.params]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integrant System Config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ig-config
  {:adapter/jetty {:port 3000
                   :handler (ig/ref :service/handler)}
   :service/handler {:datasource (ig/ref :service/datasource)}
   :service/datasource {:pool-name "service-pool"
                        :adapter "postgresql"
                        :username "postgres"
                        :password ""
                        :database-name "servicedb"
                        :server-name "localhost"
                        :port-number 5432}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middleware
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn wrap-datasource
  [handler datasource]
  (fn [request]
    (handler (assoc request :datasource datasource))))

(defn service-middleware
  [handler datasource]
  (-> handler
      (wrap-datasource datasource)
      ring.keyword-params/wrap-keyword-params
      ring.params/wrap-params
      (wrap-restful-format :formats [:json-kw])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Database Fns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn all-users
  [ds]
  (jdbc/with-db-connection [conn {:datasource ds}]
    (jdbc/query conn "select * from users")))

(defn create-user
  [ds full-name email]
  (jdbc/with-db-connection [conn {:datasource ds}]
    (jdbc/insert! conn :users {:name full-name :email email})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn index-handler
  [request]
  (let [_ (println request)]
    [::response/ok "Index"]))

(defn users-handler
  [{[_ ds] :ataraxy/result}]
  [::response/ok (all-users ds)])

(defn users-create-handler
  [{[_ ds full-name email] :ataraxy/result}]
  [::response/ok (create-user ds full-name email)])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routing & Handler Mapping
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def routes
  '{[:get "/"]                          [:index]
    ["/api" {datasource :datasource}]
    {"/users"
     {[:get]                            [:users/list datasource]
      [:post
       {{full-name :name
         email     :email}
        :params}]                       [:users/create datasource full-name email]}}})

(def handlers
  {:index               #'index-handler
   :users/list          #'users-handler
   :users/create        #'users-create-handler})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integrant Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod ig/init-key :service/handler [_ {datasource :datasource}]
  (-> {:routes routes
       :handlers handlers}
      ataraxy/handler
      (service-middleware datasource)))

(defmethod ig/init-key :adapter/jetty [_ {:keys [handler port] :as opts}]
  (jetty/run-jetty handler {:port port :join? false}))

(defmethod ig/halt-key! :adapter/jetty [_ server]
  (.stop server))

(defmethod ig/init-key :service/datasource [_ dbspec]
  (hikari/make-datasource dbspec))

(defmethod ig/halt-key! :service/datasource [_ ds]
  (hikari/close-datasource ds))
