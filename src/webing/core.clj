(ns webing.core
  (:require [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.adapter.jetty :as jetty]
            [clojure.pprint :as pprint]
            [hiccup.core :as h]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [mount.core :as mount]
            [clojure.core.async :as async]
            [taoensso.timbre :as t]))

(def db
  {:dbtype "sqlite"
   :dbname "/Users/malcolmstone/Documents/Projects/webing/webing.db"})

(mount/defstate datasource
  :start (jdbc/get-datasource db))

(mount/defstate conn
  :start (jdbc/get-connection datasource)
  :stop (.close conn))

;; TODO
;; - add some kind of background job? why did i want to do this?
;; - add layouts (again)

(defn posts-find
  []
  (sql/query conn ["select * from posts;"]))

(defn posts-find-by-id
  [id]
  (first (sql/query conn ["select * from posts where id = ?;" id])))

(defn posts-find-by-slug
  [slug]
  (first (sql/query conn ["select * from posts where slug = ?;" slug])))


(def insert-id-key (keyword "last_insert_rowid()"))

(defn posts-create
  [post]
  (t/infof "creating post %s" post)
  (let [created-map (sql/insert! conn :posts post)]
    (t/infof "created post id %s" (get created-map insert-id-key))))

(defn path-to
  ([router path-name]
   (path-to router path-name {}))
  ([router path-name path-params]
   (-> router
       (r/match-by-name path-name path-params)
       (r/match->path))))

(defn posts-view
  [router posts]
  [:div
   [:h1 "Posts2"]
   [:a {:href (path-to router ::posts-new)} "New Post"]
   [:ul
    (for [{slug :posts/slug title :posts/title} posts]
      [:li [:a {:href (path-to router ::posts-slug {:slug slug})} title]])]])

(defn post-view
  [router {title :posts/title}]
  [:div
   [:h1 title]
   [:a {:href (path-to router ::posts)} "Back to posts"]])

(defn post-new
  [router]
  [:div
   [:h1 "New post"]
   [:form {:method "post" :action (path-to router ::posts-new)}
    [:div
     [:label {:for "title"} "Title"]
     [:input#title {:type "text" :name "title"}]]
    [:div
     [:label {:for "slug"} "Slug"]
     [:input#slug {:type "text" :name "slug"}]]
    [:button {:type "submit"} "Save"]]])

(defn see-other [path]
  {:status 303
   :headers {"Location" path}})

(def router
  (ring/router
   [["/" {:name ::home
          :get (fn [_] {:status 200 :body "Home"})}]
    ["/echo" {:get (fn [req] {:status 200 :body (with-out-str (pprint/pprint req))})}]
    ["/posts"
     {:name ::posts
      :get (fn [{router :reitit.core/router}]
             (t/info "getting posts")
             (let [posts (posts-find)]
               {:status 200 :body
                (h/html (posts-view router posts))}))}]
    ["/posts/new"
     {:name ::posts-new
      :get (fn [{router :reitit.core/router}]
             {:status 200
              :body (h/html (post-new router))})
      :post (fn [{params :params
                  router :reitit.core/router}]
              (posts-create params)
              (see-other (path-to router ::posts-slug {:slug (params :slug)})))}]
    ["/posts/:slug"
     {:name ::posts-slug
      :get (fn [{{slug :slug} :path-params
                 router :reitit.core/router}]
             (let [post (posts-find-by-slug slug)]
               {:status 200 :body (h/html (post-view router post))}))}]]
   {:conflicts nil}))

(def app
  (-> (ring/ring-handler router)
      ring-keyword-params/wrap-keyword-params
      (ring-params/wrap-params {:encoding "UTF-8"})))

(mount/defstate server
  :start (jetty/run-jetty
          (fn [req] (app req))
          {:join? false :port 3000})
  :stop (.stop server))

(mount/start)
(mount/stop)