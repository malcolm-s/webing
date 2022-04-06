(ns webing.core
  (:require [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [clojure.pprint :as pprint]
            [hiccup.core :as h]
            [mount.core :refer [defstate] :as mount]))
;; TODO
;; - add sql and load posts from there
;; - add some kind of background job? why did i want to do this?
;; - set up with mount to manage the application state etc
;; - add layouts (again)
;; - add forms
;; (defstate test
;;   :start 1)
;; (mount/start)
;; (mount/stop)

(def posts
  [{:id 1 :slug "test" :title "Test"}
   {:id 2 :slug "spring-tips" :title "Spring tips"}
   {:id 3 :slug "my-clojure-journey" :title "My Clojure journey"}])

(defn find-by-slug
  [slug]
  (->> posts (filter #(= slug (% :slug))) first))


(defn path-to
  ([router path-name]
   (path-to router path-name {}))
  ([router path-name path-params]
   (-> router
       (r/match-by-name path-name path-params)
       (r/match->path))))

(defn posts-view
  [router]
  [:div
   [:h1 "Posts2"]
   [:ul
    (for [{slug :slug title :title} posts]
      [:li [:a {:href (path-to router ::posts-slug {:slug slug})} title]])]])

(defn post-view
  [router {title :title}]
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
             {:status 200 :body
              (h/html (posts-view router))})}]
    ["/posts/:slug"
     {:name ::posts-slug
      :get (fn [{{slug :slug} :path-params
                 router :reitit.core/router}]
             (let [post (find-by-slug slug)]
               {:status 200 :body (h/html (post-view router post))}))}]]))
(def app
  (ring/ring-handler router))

(def server
  (jetty/run-jetty
   (fn [req] (app req))
   {:join? false :port 3000}))

(.stop server)
(defn stop-server [server] (.stop server))

(stop-server server)
