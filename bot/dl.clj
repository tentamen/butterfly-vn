(ns dl
  (:require [clojure.core.memoize :as memo]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [cheshire.core :as json]
            [org.httpkit.client :as http]))

(def dl-server (System/getenv "DL_SERVER"))

(def db-spec {:classname "org.sqlite.JDBC"
              :subprotocol "sqlite"
              :subname ":resource:butterfly.db"})

(defn- url-encode [url] (some-> url (java.net.URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(defn- chatbot [text]
  (let [{:keys [error body]} @(http/get (str dl-server "/chatbot/" (url-encode text)))]
    (when-not error
      body)))

(def memo-chatbot
  (memo/lu chatbot :lu/threshold 100))

(defn- parse-result [body]
  (let [{:keys [label score]} (first (json/decode body true))
        [genus species] (str/split label #" ")
        latin-name (str genus " " (str/lower-case species))
        {:keys [vn_name common_name url]} (or (first (jdbc/query
                                                      db-spec
                                                      ["SELECT vn_name, common_name, url
                                                        FROM butterfly
                                                        WHERE genus = ? AND species = ?"
                                                       genus (str/lower-case species)]))
                                              {:vn_name latin-name
                                               :url "http://www.vncreatures.net/hinhanh.php?nhom=1&loai=3"})]
    [(or vn_name common_name) (str latin-name " - " score) url]))

(defn- label-image [image-url]
  (let [{:keys [status body]} @(http/get (str dl-server "/label_image")
                                         {:query-params {:image_url (url-encode image-url)
                                                         :no_predict 1}})]
    (if (= 200 status)
      [status (parse-result body)]
      [status body])))

(def memo-label-image
  (memo/lu label-image :lu/threshold 100))
