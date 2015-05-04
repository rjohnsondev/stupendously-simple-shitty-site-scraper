(ns scraper.core-test
  (:require [clojure.test :refer :all]
            [scraper.core :refer :all])
  (:import
    (java.net URL URI)))

(deftest test-get-attrs
  (let [c "<img src=\"http://test.com/images/newtour/tour_big1.gif\" />
           <br /><br />
           <link type=\"text/css\" href=\"css/style.css\" />
           No more copying and pasting URLs into emails
           <br style=\"clear:both;\" />
           <img src=\"/images/newtour/why.gif\" style=\"margin-top:50px;\" />
           </div>
           <div class=\"bigbox\" onclick=\"$('#tour1').fadeOut('slow');$('#tour2').fadeIn('slow');changecontent(2);\">
           <img src=\"http://test.com/images/newtour/tour_big2.gif\" />
           <br /><br />
           Manage and compare your options on one page
           <br style=\"clear:both;\" />
           <img src=\"/images/newtour/what.gif\"   style=\"margin-top:30px;\"/>
           </div>
           <div class=\"bigbox\" onclick=\"$('#tour1').fadeOut('slow');$('#tour2').fadeIn('slow');changecontent(3);\">
           <img src=\"http://test.com/images/newtour/tour_big3.gif\"/>
           <a href=\"//google.com\">test!</a>
           <br /><br />   
           Get your friends and family to contribute to your decision
           <br style=\"clear:both;\" />
           <img src=\"/images/newtour/how.gif\"   style=\"margin-top:30px;\"/>       
           <a href=\"//google.net\"  title=\"test\"       >test!</a>
           <a  title=\"test\"  href=\"//google.net\"      >test!</a>
           </div>
           <div class=\"bigbox\" onclick=\"$('#tour1').fadeOut('slow');$('#tour2').fadeIn('slow');changecontent(4);\">
           <img src=\"http://test.com/images/newtour/tour_big4.gif\"/>
          <th><a href=\"\" id=\"support-ops\">Support &#38; Operations</a></th>
           <br /><br />"]
    (testing "Image attrs"
      (is (= '(["\"http://test.com/images/newtour/tour_big1.gif\"" "http://test.com/images/newtour/tour_big1.gif"]
               ["\"/images/newtour/why.gif\"" "/images/newtour/why.gif"]
               ["\"http://test.com/images/newtour/tour_big2.gif\"" "http://test.com/images/newtour/tour_big2.gif"]
               ["\"/images/newtour/what.gif\"" "/images/newtour/what.gif"]
               ["\"http://test.com/images/newtour/tour_big3.gif\"" "http://test.com/images/newtour/tour_big3.gif"]
               ["\"/images/newtour/how.gif\"" "/images/newtour/how.gif"]
               ["\"http://test.com/images/newtour/tour_big4.gif\"" "http://test.com/images/newtour/tour_big4.gif"])
             (get-attrs c "img" "src"))))
    (testing "Link hrefs"
      (is (= '(["\"//google.com\"" "//google.com"]
               ["\"//google.net\"" "//google.net"]
               ["\"//google.net\"" "//google.net"])
             (get-attrs c "a" "href"))))
    (testing "CSS link hrefs"
      (is (= '(["\"css/style.css\"" "css/style.css"])
             (get-attrs c "link" "href"))))
    (testing "match all tags"
      (is (= 4
             (count (get-attrs c nil "href"))))
      (is (= 7
             (count (get-attrs c nil "src")))))
    (testing "quoted attrs with spaces"
      (is (= ["\"background: url(/img/test.gif);\"" "background: url(/img/test.gif);"]
          (first (get-attrs "<img style=\"background: url(/img/test.gif);\" />" nil "style")))))
    (testing "unenclosed attr"
      (is (= ["test" "test"]
          (first (get-attrs "<img src=test />" "img" "src")))
    (testing "with space"
      (is (= ["http://google.com" "http://google.com"]
          (first (get-attrs "<img src= http://google.com />" "img" "src")))))))))

(deftest test-get-base
  (testing "Gets base URL from content"
    (is (= (URI. "http://test.com/")
           (get-base "<base href=\"http://test.com/\" />"))))
  (testing "Nil on missing base"
    (is (= nil
           (get-base "<notabase href=\"http://test.com/\" />")))))

(deftest test-remove-parent-path
  (testing "/.. recursively removed"
    (is (= (remove-parent-path "/../../../test")
           "/test"))
    (is (= (remove-parent-path "/../test")
           "/test"))
    (is (= (remove-parent-path "test")
           "test"))))

(deftest test-squash-above-root-path
  (testing "path updated in urls"
    (is (= (squash-above-root-path (URI. "http://test.com/../../test"))
           (URI. "http://test.com/test")))
    (is (= (squash-above-root-path (URI. "http://test.com/test"))
           (URI. "http://test.com/test")))))

(deftest test-get-absolute-urls
  (testing "Resolves correctly"
    (is (= {"img/title.gif" (URI. "http://google.com/test/img/title.gif")}
           (get-absolute-urls 
             '(["img/title.gif" "img/title.gif"])
             (URI. "http://google.com/test/test.html")))))
  (testing "Absolute URLs are untouched"
    (is (= {"http://google.com/img/title.gif" (URI. "http://google.com/img/title.gif")}
           (get-absolute-urls 
             '(["http://google.com/img/title.gif" "http://google.com/img/title.gif"])
             (URI. "http://asdf.com/")))))
  (testing "Unparsable urls are simply skipped."
    (is (= {"http://google.com/img/title.gif" (URI. "http://google.com/img/title.gif")}
           (get-absolute-urls 
             '(["#$%^%$&^&*" "#$%^%$&^&*"]
               ["http://google.com/img/title.gif" "http://google.com/img/title.gif"])
             (URI. "http://asdf.com/")))))
  (testing "we encode spaces"
    (is (= {"http://google.com/img/title with space.gif" (URI. "http://google.com/img/title%20with%20space.gif")}
           (get-absolute-urls 
             '(["http://google.com/img/title with space.gif" "http://google.com/img/title with space.gif"])
             (URI. "http://asdf.com/"))))))

(deftest test-filter-http-urls
  (testing "non-http urls are removed"
    (is (= 2
           (count
             (filter-http-urls
               [["http://google.com" (URI. "http://google.com")]
                ["ftp://google.com" (URI. "ftp://google.com")]
                ["http://test.com" (URI. "http://test.com")]]))))))

(deftest test-filter-domain
  (testing "external domains are removed"
    (is (= 2
           (count (filter-domain [["http://google.com" (URI. "http://google.com")]
                                  ["ftp://google.com" (URI. "ftp://google.com")]
                                  ["http://test.com" (URI. "http://test.com")]]
                                 #{"google.com"}))))))

; https://gist.github.com/edw/5128978
(defn delete-recursively [fname]
  (let [func (fn [func f]
               (when (.isDirectory f)
                 (doseq [f2 (.listFiles f)]
                   (func func f2)))
               (clojure.java.io/delete-file f))]
    (func func (clojure.java.io/file fname))))

(deftest test-make-dirs
  (if (.exists (clojure.java.io/file "/tmp/scraper-test"))
    (delete-recursively "/tmp/scraper-test"))
  (testing "directories are created"
    (make-dirs (clojure.java.io/file "/tmp/scraper-test/one/two"))
    (is (= 3
           (count (file-seq (clojure.java.io/file "/tmp/scraper-test"))))))
  (testing "creations are idempontent"
    (make-dirs (clojure.java.io/file "/tmp/scraper-test/one"))
    (is (= 3
           (count (file-seq (clojure.java.io/file "/tmp/scraper-test"))))))
  (testing "existing files are moved"
    (spit "/tmp/scraper-test/three" "test")
    (make-dirs (clojure.java.io/file "/tmp/scraper-test/three/four"))
    (is (.exists (clojure.java.io/file "/tmp/scraper-test/three/index.html")))))

(deftest test-filename-from-url
  (testing "local representation"
    (is (= (filename-from-url (URI. "http://test.com/media/new.css") "/tmp/scraper-test/")
           "/tmp/scraper-test/test.com/media/new.css")))
  (testing "replacement representation"
    (is (= (filename-from-url (URI. "http://test.com/media/new.css#asdf") "/")
           "/test.com/media/new.css")))
  (testing "replaces bad chars"
    (is (= (filename-from-url (URI. "http://test.com/media/test?a=b&b=c*") "/")
           "/test.com/media/testa=b_b=c_")))
  (testing "double slashes"
    (is (= (filename-from-url (URI. "http://example.com/media/1//new.css") "/")
           "/example.com/media/1/new.css"))))

(deftest test-get-save-path
  (if (.exists (clojure.java.io/file "/tmp/scraper-test"))
    (delete-recursively "/tmp/scraper-test"))
  (testing "parent directories created"
    (get-save-path (URI. "http://test.com/media/new.css#asdf") "/tmp/scraper-test/")
    (is (.exists (clojure.java.io/file "/tmp/scraper-test/test.com/media"))))
  (testing "path is correct"
    (is (= (str (get-save-path (URI. "http://test.com/media/new.css#asdf") "/tmp/scraper-test/"))
           "/tmp/scraper-test/test.com/media/new.css")))
  (testing "index.html returned for empty existing path"
    (is (= (str (get-save-path (URI. "http://test.com/media") "/tmp/scraper-test/"))
           "/tmp/scraper-test/test.com/media/index.html")))
  (testing "problematic chars"
    (is (= (str (get-save-path (URI. "http://test.com/sdfg?a=/sdfasd%5B%2F%3C%3E%7C%3A%26%5Dfg") "/tmp/scraper-test/"))
           "/tmp/scraper-test/test.com/sdfga=_sdfasd[______]fg")))
  (testing "root filename"
    (is (= (str (get-save-path (URI. "http://test.com/") "/tmp/scraper-test/"))
           "/tmp/scraper-test/test.com/index.html"))))

(deftest test-mark-downloaded
  (testing "entries are marked as downloaded"
    (mark-downloaded-list ["http://google.com"
                           "http://slashdot.org"])
    (is (downloaded? (URI. "http://google.com")))
    (is (not (downloaded? (URI. "http://google1.com"))))))

(deftest test-update-content-links
  (testing "Updates src"
    (let [c (str "<script src='http://static.test.com/js/functions.js' type=\"text/javascript\"></script>"
                 "url('http://static.test.com/media/test.img');'")]
      (is (= (update-content-links
               c
               [["'http://static.test.com/js/functions.js'"
                 (URI. "http://static.test.com/js/functions1.js")]
                ["('http://static.test.com/media/test.img')"
                 (URI. "http://static.test.com/media/test1.img")]])
             (str "<script src='/static.test.com/js/functions1.js' type=\"text/javascript\"></script>"
                  "url('/static.test.com/media/test1.img');'"))))))

(deftest test-update-base-link
  (testing "updates base"
    (is (= (update-base-link "testing<base href=\"http://test.com/\" />asdfasdf asf s")
           "testing<base href=\"/\" />asdfasdf asf s"))
    (is (= (update-base-link "testing<base href=http://test.com >asdfasdf asf s")
           "testing<base href=\"/\" >asdfasdf asf s"))
    (is (= (update-base-link "testing<base href=http://test.com>asdfasdf asf s")
           "testing<base href=\"/\">asdfasdf asf s"))))

; NB: makes http request to example.com!
(deftest test-get-page
  (testing "404"
    (is (= 404
          (get (get-page (URI. "http://example.com/404")) :status)))))

(deftest test-extract-urls
  (testing "extracts a bunch of urls"
    (let [c "<link rel=\"P3Pv1\" href=\"/w3c/p3p.xml\" />
             <link rel=\"stylesheet\" type=\"text/css\" href='http://static.example.com/js/yui/build/button/assets/customButton.css' />
             <link rel='shortcut icon' href='/accou\"nts/acc_1/images/_0/favicon.ico'/> 
             <script src=\"http://static.exa'mple.com/js/functions.js\" type=\"text/javascript\"></script>
             <script src=\"http://static.example.com/js/projax/prototype.js\" type=\"text/javascript\"></script>
             <script src=\"http://static.example.com/js/projax/scriptaculous.js\" type=\"text/javascript\"></script>"]
      (is (= (count (extract-urls c))
             6))))
  (testing "extracts css urls"
    (let [c "body {
             background: #83bfdb url(http://static.example.com/accounts/acc_1/images/_0/bg_grad.gif) 0 0 repeat-x;
             background: #83bfdb url(\"//static.ex'ample.com/accounts/acc_1/images/_0/bg_grad.gif\") 0 0 repeat-x;
             background: #83bfdb url(/accounts/acc_1/images/_0/bg_grad.gif) 0 0 repeat-x;
             background: #83bfdb url('bg_gr\"ad.gif') 0 0 repeat-x;
             background:url('http://static.example.com/images//icons/speech.gif') no-repeat;
             text-align: center; /* centre in IE */
             line-height: 1em;
             font: 80% Arial, Helvetica, sans-serif;
             color: #666666;
             padding-bottom: 20px;
             }"]
      (is (= (extract-css-urls c)
             '(["(http://static.example.com/accounts/acc_1/images/_0/bg_grad.gif)" "http://static.example.com/accounts/acc_1/images/_0/bg_grad.gif"]
               ["(\"//static.ex'ample.com/accounts/acc_1/images/_0/bg_grad.gif\")" "//static.ex'ample.com/accounts/acc_1/images/_0/bg_grad.gif"]
               ["(/accounts/acc_1/images/_0/bg_grad.gif)" "/accounts/acc_1/images/_0/bg_grad.gif"]
               ["('bg_gr\"ad.gif')" "bg_gr\"ad.gif"]
               ["('http://static.example.com/images//icons/speech.gif')" "http://static.example.com/images//icons/speech.gif"]))))))

(deftest test-extract-inline-css
  (testing "finds css urls"
    (is (= (extract-inline-css "<img style=\"background: url(/img/test1.gif); background-image: url('test/test2.gif');\" /><img style=\"background: url(/img/test3.gif);\" />")
           '(["(/img/test1.gif)" "/img/test1.gif"]
             ["('test/test2.gif')" "test/test2.gif"]
             ["(/img/test3.gif)" "/img/test3.gif"])))))

(deftest test-update-html-respoonse
  ; note that utf-8 should still be fine despite windows charset used internally
  ; as it's a charset, not an encoding it shouldn't bork.
  (let [r (update-html-response
            {:body (.getBytes "<a href=\"http://test.com/nextpage\">test 日本</a><a href='//out.com'>out</a>" "UTF-8")
             :headers {"Content-Type" "text/html"}
             :trace-redirects ["http://test.com/index.html"]}
            #{"test.com"})]
    (testing "links are updated, external ignored."
      (is (= (String. (get r :updated-body))
             "<a href=\"/test.com/nextpage\">test 日本</a><a href='//out.com'>out</a>")))
    (testing "links list is populated"
      (is (= (last (first (get r :links)))
             (URI. "http://test.com/nextpage"))))
    (testing "external links not included for spidering"
      (is (= (count (get r :links))
             1)))))

(deftest test-save-url
  (if (.exists (clojure.java.io/file "/tmp/scraper-test"))
    (delete-recursively "/tmp/scraper-test"))
  (testing "saves to file, returns links"
    (reset-downloaded) 
    (is (= (count
             (get
               (save-url
                 (URI. "http://example.com/index.html")
                 #{"example.com" "www.iana.org"}
                 "/tmp/scraper-test/")
               :links))
           1))
    (is (.exists (clojure.java.io/file "/tmp/scraper-test/example.com/index.html")))))

