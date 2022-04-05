(ns webing.core
  (:require [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [clojure.pprint :as pprint]
            [hiccup.core :as h]
            [mount.core :refer [defstate] :as mount]))

(defstate test
  :start 1)
(mount/start)
(mount/stop)
test
;; (def router
;;   (r/router
;;    [["/" ::home]
;;     ["/api/ping" ::ping]
;;     ["/api/orders/:id" ::order]]))

;; (def router2
;;   (ring/router
;;    [["/test" ::test {:get (fn [] {:status 200 :body "Hello"})}]
;;     ["/api" ::home]
;;     ["/api/ping" ::ping]
;;     ["/api/orders/:id" ::order]]))
;; (r/match-by-name)
;; (def app
;;   (ring/ring-handler router2))

;; (app {:request-method :get
;;       :uri "/api/test"
;;       :query-params {:x "1", :y "2"}})
(def posts
  [{:id 1 :slug "test" :title "Test"}])

(defn find-by-slug
  [slug]
  (->> posts (filter #(= slug (% :slug))) first))

(defn posts-view
  []
  [:div
   [:h1 "Posts2"]
   [:ul
    (for [{slug :slug title :title} posts]
      [:li [:a {:href (path-to ::posts-slug {:slug slug})} title]])]])

(defn post-view
  [{title :title}]
  [:div
   [:h1 title]
   [:a {:href (path-to ::posts)} "Back to posts"]])

(def router
  (ring/router
   [["/" {:name ::home
          :get (fn [req] {:status 200 :body "Home"})}]
    ["/test" {:get (fn [req] {:status 200 :body "Hello there"})}]
    ["/echo" {:get (fn [req] {:status 200 :body (with-out-str (pprint/pprint req))})}]
    ["/posts" {:name ::posts
               :get (fn [req] {:status 200 :body
                               (h/html (posts-view))})}]
    ["/posts/:slug"
     {:name ::posts-slug
      :get (fn [{{slug :slug} :path-params}]
             (let [post (find-by-slug slug)]
               {:status 200 :body (h/html (post-view post))}))}]]))
(def app
  (ring/ring-handler router))

(defn path-to
  ([path-name]
   (path-to path-name {}))
  ([path-name path-params]
   (-> app
       (ring/get-router)
       (r/match-by-name path-name path-params)
       (r/match->path))))

(def server
  (jetty/run-jetty
   (fn [req] (app req))
   {:join? false :port 3000}))

(.stop server)
(defn stop-server [server] (.stop server))

(stop-server server)
