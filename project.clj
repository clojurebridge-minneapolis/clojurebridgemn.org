(defproject clojurebridgemn "0.2.3"
  :description "ClojureScriptMN.org website"
  :url "https://github.com/clojurebridge-minneapolis/clojurebridgemn.org"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [aleph "0.4.0"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [ring "1.3.2" :exclusions [org.clojure/tools.namespace]]
                 [ring/ring-defaults "0.1.5"]
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [commons-codec "1.10"]
                 [compojure "1.3.4" :exclusions [commons-codec]]
                 [enlive "1.1.5"]
                 [cheshire "5.4.0"]
                 [environ "1.0.0"]
                 ;; cljs
                 [org.omcljs/om "0.8.8"]
                 [sablono "0.3.4"]
                 [secretary "1.2.3"]
                 [cljs-http "0.1.35"]]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.3.1"
             :exclusions [org.clojure/clojure]]
            [lein-environ "1.0.0"]]

  :hooks [leiningen.cljsbuild]

  :figwheel {:css-dirs ["resources/public/css"]
             :open-file-command "myfile-opener"
             :ring-handler clojurebridgemn.server/info-handler}

  :source-paths ["src/main/clj"]
  :target-path "target/%s" ;; avoid AOT problems
  :clean-targets ^{:protect false}
  ["resources/public/js/compiled" :target-path :compile-path]

  :uberjar-name "clojurebridgemn.jar"
  :uberjar-exclusions [#"META-INF/maven.*" #".*~$"]
  :main clojurebridgemn.server
  :aot [clojurebridgemn.server]

  :aliases {"clean-test" ^{:doc "Clean and run all tests."}
            ["do" "clean" ["test"]]
            "figwheel-test" ^{:doc "Will compile figwheel for the :test profile."}
            ["with-profile" "-dev,+test" "figwheel"]
            "prod" ^{:doc "Produce uberjar."}
            ["do" "clean" ["with-profile" "-dev,+prod" "uberjar"]]}

  :profiles
  {:dev {:env {:program-mode :dev}
         :dependencies [[net.info9/clj-webdriver "0.7.4"]]
         :test-paths ["src/test/clj"]
         :cljsbuild
         {:builds
          {:app
           {:source-paths ["src/main/cljs"]
            :figwheel {:websocket-host
                       ;; "localhost"
                       "m.info9.net"
                       }
            :compiler {:main clojurebridgemn.client
                       :output-dir "resources/public/js/compiled"
                       :output-to  "resources/public/js/compiled/app.js"
                       :asset-path "js/compiled"
                       :source-map true
                       :source-map-timestamp true
                       :verbose true
                       :cache-analysis true
                       :optimizations :none
                       :pretty-print false}}}
          :test-commands
          { "phantomjs" ["phantomjs" "src/test/phantomjs/unit-test.js"
                         "target/test/index.html"]
            "selenium" ["lein-selenium"]}}}

   :test {:env {:program-mode :test}
          :cljsbuild
          {:builds
           {:app
            {:source-paths ["src/main/cljs" "src/test/cljs"]
             :figwheel {:websocket-host "localhost"}
             :compiler {:main testing.runner
                        :output-dir    "target/test/js/compiled"
                        :output-to     "target/test/js/compiled/app.js"
                        :source-map    true
                        :asset-path    "js/compiled"
                        :verbose true
                        :cache-analysis true
                        :optimizations :none
                        :pretty-print  false}}}}}

   :prod {:env {:program-mode :prod}
          :cljsbuild
          {:builds
           {:app
            {:source-paths ["src/main/cljs" "src/prod/cljs"]
             :compiler {:main clojurebridgemn.client
                        :output-dir "resources/public/js/compiled"
                        :output-to  "resources/public/js/compiled/app.js"
                        :asset-path "js/compiled"
                        :verbose true
                        :cache-analysis false
                        :optimizations :advanced
                        :pretty-print false}}}}}}

   :uberjar {:omit-source true
             :aot :all})
