(ns webing.core
  (:require [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [clojure.pprint :as pprint]
            [hiccup.core :as h]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [mount.core :refer [defstate] :as mount]))

(def db {:dbtype "sqlite" :dbname "/Users/malcolmstone/Documents/Projects/webing/webing.db"})
(def ds (jdbc/get-datasource db))
(def conn (jdbc/get-connection ds))
(sql/query conn ["select * from posts;"])
(sql/query conn ["select * from posts where id = ?", 0])
(sql/insert! conn :posts {:slug "spring-tips" :title "Spring tips"})

;; TODO
;; - add some kind of background job? why did i want to do this?
;; - set up with mount to manage the application state etc
;; - add layouts (again)
;; - add forms
;; (defstate test
;;   :start 1)
;; (mount/start)
;; (mount/stop)

(defn posts-find
  []
  (sql/query conn ["select * from posts;"]))

(defn posts-find-by-slug
  [slug]
  (first (sql/query conn ["select * from posts where slug = ?;" slug])))

(defn path-to
  ([router path-name]
   (path-to router path-name {}))
  ([router path-name path-params]
   (-> router
       (r/match-by-name path-name path-params)
       (r/match->path))))

(defn posts-view
  [router posts]
  (prn posts)
  [:div
   [:h1 "Posts2"]
   [:ul
    (for [{slug :posts/slug title :posts/title} posts]
      [:li [:a {:href (path-to router ::posts-slug {:slug slug})} title]])]])

(defn post-view
  [router {title :posts/title}]
  [:div
   [:h1 title]
   [:a {:href (path-to router ::posts)} "Back to posts"]])

(def router
  (ring/router
   [["/" {:name ::home
          :get (fn [req] {:status 200 :body "Home"})}]
    ["/test" {:get (fn [req] {:status 200 :body "Hello there"})}]
    ["/echo" {:get (fn [req] {:status 200 :body (with-out-str (pprint/pprint req))})}]
    ["/posts"
     {:name ::posts
      :get (fn [{router :reitit.core/router}]
             (let [posts (posts-find)]
               {:status 200 :body
                (h/html (posts-view router posts))}))}]
    ["/posts/:slug"
     {:name ::posts-slug
      :get (fn [{{slug :slug} :path-params
                 router :reitit.core/router}]
             (let [post (posts-find-by-slug slug)]
               {:status 200 :body (h/html (post-view router post))}))}]]))
(def app
  (ring/ring-handler router))

(def server
  (jetty/run-jetty
   (fn [req] (app req))
   {:join? false :port 3000}))

(.stop server)
