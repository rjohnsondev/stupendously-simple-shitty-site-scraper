(ns scraper.core
  (:require [clj-http.client :as client]
            [clojure.tools.cli :refer [parse-opts]])
  (:import (java.io File)
           (java.net URI URL)
           (java.nio.file Files))
  (:gen-class))

;; So yeah, not the most idiomatic clojure here, but let's just remember why
;; you are using this tool before passing judgement.

(defn get-match-seq [contents re]
  (let [matcher (re-matcher re contents)]
    (remove
      #(empty? (last %))
      (map
        #(let [v (second %)]
           (vector
             v
             (if (< -1 (.indexOf "\"'" (.substring v 0 1)))
               (.substring v 1 (- (.length v) 1))
               v)))
        (take-while
          (complement nil?)
          (repeatedly
            #(re-find matcher)))))))

(defn get-attrs [contents tag attr]
  "Takes a string of contents, a tag and attribute name to extract and
  returns a lazy seq of vectors representing the match groups. The first
  group contains the attribute value. If tag is null, matches all tags."
  (let [tag (if (nil? tag) "[a-zA-Z]+" tag)]
      (concat (get-match-seq
                contents
                (re-pattern
                  (str "<" tag " [^>]* ?" attr "= ?([^\"'].+?)( |>|/>)")))
              (get-match-seq
                contents
                (re-pattern
                  (str "<" tag " [^>]* ?" attr "= ?('.*?')")))
              (get-match-seq
                contents
                (re-pattern
                  (str "<" tag " [^>]* ?" attr "= ?(\".*?\")"))))))

(defn get-base [contents]
  "Attempt to retrieve the base URL from content and return it as a
  vector containing the original link text and a resolved URI. Returns
  nil if no base url is specified."
  (some-> contents
          (get-attrs "base" "href")
          first
          last
          (URI.)))

(defn remove-parent-path [s]
  "Strip of leading '/..' chars."
  (if (and s
           (>= (.length s) 3)
           (= (subs s 0 3) "/.."))
    (remove-parent-path (subs s 3))
    s))

(defn squash-above-root-path [u]
  "Remove paths attempting to go above root."
  (if (.startsWith (.getScheme u) "http")
    (URI. (.getScheme u)
          (.getAuthority u)
          (remove-parent-path (.getPath u))
          (.getQuery u)
          (.getFragment u))
    u))

(defn get-absolute-urls [urls u]
  "Takes a seq of original url text, clean url pairs in a vector. The clean
  url text is parsed and resolved against u to produce an absolute URI.
  The result is a map containing original url text to resovled URI objects."
  (reduce
    (fn [r [x y]]
      (if (nil? x)
        r
        (try
          (assoc r x 
                 (squash-above-root-path
                   (.resolve u (.replaceAll y " " "%20"))))
          (catch java.lang.IllegalArgumentException e
            (println y e)
            r))))
    {}
    urls))

(defn filter-http-urls [urls]
  "Filter a seq of vectors containing source html text & resolved URI,
   removing any URIs that are not of the http(s) protocol."
  (filter
    #(.startsWith (.getScheme (last %)) "http")
    urls))

(defn filter-domain [urls domains]
  "Filter a seq of vectors containing source html text & resolved URI,
   removing any URIs that are not contained in domains."
  (filter
    (fn [[x y]] (contains? domains (.getHost y)))
    urls))

(defn make-dirs [f]
  "Create a directory at the path f.  If a file already exists at that
   location, in one of the parent locations, instead of overwriting it,
   move it to 'index.html' within the created directory."
  (if (and (not (nil? (.getParentFile f)))
           (not (.isDirectory (.getParentFile f))))
    (make-dirs (.getParentFile f)))
  (if (and (.exists f)
           (not (.isDirectory f)))
    (let [tmp (File/createTempFile "scraper" "tmp")]
      (.renameTo f tmp)
      (.mkdir f)
      (.renameTo tmp (File. (str (.toString f) "/index.html")))))
  (.mkdir f))

(defn filename-from-url [u prefix]
  "Create a filename string from URI u by using the host, path and query
  strings, replacing special chars."
  (.replaceAll
    (str prefix
         (.getHost u)
         (.getPath u)
         (.replaceAll (str (.getQuery u)) "[/<>|:&*]" "_"))
    "//*" "/"))

(defn get-save-path
  "Return a File representing an appropriate location for saving of the
   URI u.  Parent paths are created through make-dirs as required (side-effect).
   If the resultant path exists as a directory, the target becomes index.html
   in the directory."
  ([u prefix]
   (let [filename (filename-from-url u prefix)
         f (File. filename)]
     (make-dirs (.getParentFile f))
     (if (and (.exists f)
              (.isDirectory f))
       (File. (str filename "/index.html"))
       f))))

(def downloaded-list (atom #{}))
(defn reset-downloaded []
  (reset! downloaded-list #{}))
(defn mark-downloaded [u]
  (swap! downloaded-list #(conj % (str u))))
(defn mark-downloaded-list [redirects]
  (doall (map #(mark-downloaded (URI. %)) redirects)))
(defn downloaded? [u]
  (contains? @downloaded-list (str u)))

(defn update-content-links [c urls]
  "Given a string of content c, update URLs with local equivalents from the map
  of plain text to resolved urls."
  (reduce
    (fn [s [url-str new-url]]
      (let [f (cond
                (.startsWith url-str "('") (str "('" (filename-from-url new-url "/") "')")
                (.startsWith url-str "(\"") (str "(\"" (filename-from-url new-url "/") "\")")
                (.startsWith url-str "\"") (str "\"" (filename-from-url new-url "/") "\"")
                (.startsWith url-str "'") (str "'" (filename-from-url new-url "/") "'")
                (.startsWith url-str "(") (str "(" (filename-from-url new-url "/") ")")
                :else url-str)]
        (.replace s url-str f)))
    c
    urls))


(defn update-base-link [c]
  "Replace the existing url for the html base metadata with '/' given the
   original string."
  (clojure.string/replace
    c
    #"(<base href=) ?(.+?)( |>|/>)"
    "$1\"/\"$3"))

(defn get-page [u]
  "Attempt to retrieve the page at u. If a non 2XX response or a timeout
   occurs, response dict is extracted and returned from ex-data."
  (try
    (client/get
      (.toString u)
      {:socket-timeout 20000
       :conn-timeout 20000
       :as :byte-array})
    (catch java.net.SocketTimeoutException e
      (println e)
      (get (ex-data e) :object))
    (catch clojure.lang.ExceptionInfo e
      (println e)
      (get (ex-data e) :object))))

(defn extract-css-urls [c]
  "Extract spiderable URLs from css content c."
  (let [re #"url(\(\"?'?.*?'?\"?\))"
        matcher (re-matcher re c)]
    (map 
      #(let [v (second %)
             sv (.substring v 1 (- (.length v) 1))]
         (vector
           v
           (if (< -1 (.indexOf "\"'" (.substring sv 0 1)))
             (.substring sv 1 (- (.length sv) 1))
             sv)))
      (take-while
        (complement nil?)
        (repeatedly
          #(re-find matcher))))))

(defn extract-inline-css [c]
  "Find any css urls embedded in style attributes."
  (apply
    concat
    (map
      #(extract-css-urls (last %))
      (get-attrs c nil "style"))))

(defn extract-urls [c]
  "Extract spiderable URLs from html content c."
  (concat
    (get-attrs c "link" "href")
    (get-attrs c "img" "src")
    (get-attrs c "script" "src")
    (get-attrs c "a" "href")
    (extract-inline-css c)))

(defn get-file-options []
  "Return a decent default set of options for Files.write"
  (let [a (make-array 
            java.nio.file.OpenOption
            1)]
    (aset a 0 java.nio.file.StandardOpenOption/CREATE)
    a))

(defn html-response? [r]
  (.contains (str (get r :headers "Content-Type")) "html"))

(defn css-response? [r]
  (.contains (str (get r :headers "Content-Type")) "text/css"))

(defn save-response! [r prefix valid-domains]
  "Save a response to a local files representing each redirect in the response.
  Uses the :updated-body key if it exists, otherwise falls back to :body."
  (doall
    (map
      (fn [u]
        (if (contains? valid-domains (.getHost (URI. u)))
          (let [sp (get-save-path (URI. u) prefix)]
            (println "saving to " sp)
            (Files/write
              (.toPath sp)
              (or (get r :updated-body) (get r :body))
              (get-file-options)))))
      (get r :trace-redirects))))

(defn update-html-response [r valid-domains]
  "Updates a response object with an :updated-body which is the :body with all
   the links updated to point to their local equivalents.  The :links value is
   also populated with all links out from the page, filtered by valid-domains.
   Note that the body is interpreted using iso-8859-1 'cause UTF encoding will
   choke on bad chars."
  (if (or (html-response? r)
          (css-response? r))
    (let [c (String. (get r :body) "iso-8859-1")
          b (get-base c)
          d (URI. (last (get r :trace-redirects)))
          urls (-> (if (css-response? r)
                     (extract-css-urls c)
                     (extract-urls c))
                   (get-absolute-urls (or b d))
                   filter-http-urls
                   (filter-domain valid-domains))
          c (-> c
                (update-content-links urls)
                (update-base-link))]
      (merge r {:updated-body (.getBytes c "iso-8859-1")
                :links urls}))
    r))

(defn save-url [u valid-domains prefix]
  "Retrieve and save the URI u to its local representation, including
  updating internal links and all intermediate redirects.  Returns
  the updated response object."
  (when (and
          (not (downloaded? u))
          (contains? valid-domains (.getHost u)))
    (println "downloading " u)
    (let [r (update-html-response (get-page u) valid-domains)
          redirects (get r :trace-redirects)]
      (mark-downloaded-list redirects)
      (if (= (get r :status) 200)
        (save-response! r prefix valid-domains)
        (println "Error retrieving " u (get r :status) ": " (get r :body)))
      r)))

(defn squash-fragment [u]
  "Remove the fragment from a URI."
  (URI. (.getScheme u)
        (.getAuthority u)
        (.getPath u)
        (.getQuery u)
        nil))

(defn spider-page [pages valid-domains prefix]
  "Downloads the set of URIs pages, returning a new set of links for the next
  depth. Fragments are removed to prevent double-downloads."
  (reduce
    (fn [s u]
      (println "entry " u)
      (let [r (save-url u valid-domains prefix)]
        (into
          s
          (remove
            downloaded?
            (map
              #(squash-fragment (last %))
              (get r :links))))))
    #{}
    pages))

(defn spider [pages valid-domains prefix max-depth]
  (loop [pages pages
         depth 0]
    (println "At depth: " depth)
    (let [new-pages (spider-page pages valid-domains prefix)]
      (println new-pages)
      (if (= (count new-pages) 0)
        (println "Done!")
        (if (> depth max-depth)
          (println "Max depth exceeded, that'll do.")
          (recur new-pages (inc depth)))))))


(defn usage [options-summary]
  (->> ["Stupendously Simple Shitty Site Scraper"
        ""
        "Usage: scraper [options] URL"
        ""
        "Options:"
        options-summary]
       (clojure.string/join \newline)))

(def cli-options
  [["-m" "--max-depth DEPTH" "Maximum spidering recursion depth"
    :parse-fn #(Integer/parseInt %)
    :default 15]
   ["-d" "--domains DOMAINS" "Comma separated list of valid domains"]
   ["-o" "--output OUTPUT" "Local directory to save to"
    :default "resources/public"]
   ["-h" "--help"]])

(defn parse-domains-option [u options]
  "Convert the domains from a comma separated list, or default to u's host."
  (into
    #{}
    (clojure.string/split
      (str (or (get options :domains)
               (.getHost u)))
      #",")))

(defn parse-prefix [options]
  "Ensure the prefix pulled out of options ends with a trailing slash."
  (let [output (get options :output)]
    (if (not= (last output)
              \/)
      (str output "/")
      output)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        u (URI. (or (last arguments) ""))
        domains (parse-domains-option u options)
        prefix (parse-prefix options)
        depth (get options :max-depth)]
    (cond
      (nil? u) (exit 0 (usage summary))
      (:help options) (exit 0 (usage summary)))
    (println "Saving" u "to" prefix "with depth" depth "without leaving domains" domains)
    (spider #{u} domains prefix depth))
  (println "Done!"))

