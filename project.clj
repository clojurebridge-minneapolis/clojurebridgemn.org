(defproject clojurebridgemn "0.6.0"
  :description "ClojureScriptMN.org website"
  :url "https://github.com/clojurebridge-minneapolis/clojurebridgemn.org"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.293" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]
                 [potemkin "0.4.3"]
                 [aleph "0.4.1"
                  ;; :exclusions [clj-tuple]
                  ]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [compojure "1.5.1"
                  :exclusions [commons-codec]
                  ]
                 [enlive "1.1.6"]
                 [cheshire "5.6.3"]
                 [environ "1.1.0"]
                 ;; cljs
                 [cljsjs/react-dom-server "0.14.3-0"] ;; for sablono
                 [org.omcljs/om "1.0.0-alpha28"]
                 [sablono "0.5.3"]
                 [secretary "1.2.3"]
                 [cljs-http "0.1.42"
                  :exclusions [org.clojure/core.async
                               org.clojure/tools.analyzer.jvm
                               com.cognitect/transit-cljs
                               com.cognitect/transit-js
                               org.clojure/core.memoize
                               org.clojure/core.cache
                               org.clojure/data.priority-map ]
                  ]
                 ;; the following are to resolve dependency conflicts
                 ;; [org.clojure/tools.reader "1.0.0-alpha3"] ;; figwheel
                 ;; [commons-codec "1.10"] ;; compojure
                 ;; [org.apache.httpcomponents/httpcore "4.4.3"] ;; clj-webdriver
                 ]

  :plugins [[lein-cljsbuild "1.1.4" :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.5.8"
             ;; :exclusions [org.clojure/clojure
             ;;              org.clojure/tools.reader
             ;;              ring/ring-core]
             ]
            [lein-environ "1.1.0"]]

  :jvm-opts ["--add-modules" "java.xml.bind"]

  :hooks [leiningen.cljsbuild]

  :figwheel {:css-dirs ["resources/public/css"]
             ;; :open-file-command "myfile-opener"
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
         ;; :dependencies [[org.seleniumhq.selenium/selenium-java "2.48.2"
         ;;                 :exclusions [org.eclipse.jetty/jetty-io
         ;;                              org.eclipse.jetty/jetty-util]]
         ;;                [clj-webdriver "0.7.2"]]
         :test-paths ["src/test/clj"]
         :cljsbuild
         {:builds
          {:app
           {:source-paths ["src/main/cljs"]
            :figwheel {:websocket-host :js-client-host
                       :on-jsload "clojurebridgemn.client/figwheel-reload"}
            :compiler {:main clojurebridgemn.client
                       :closure-defines {"clojurebridgemn.utils.program_mode" "dev"}
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
             :figwheel {:websocket-host
                        :js-client-host}
             :compiler {:main testing.runner
                        :closure-defines {"clojurebridgemn.utils.program_mode" "test"}
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
            {:source-paths ["src/main/cljs"]
             :compiler {:main clojurebridgemn.client
                        :closure-defines {"clojurebridgemn.utils.program_mode" "prod"}
                        :output-dir "resources/public/js/compiled"
                        :output-to  "resources/public/js/compiled/app.js"
                        :asset-path "js/compiled"
                        :verbose true
                        :cache-analysis false
                        :optimizations :advanced
                        :pretty-print false}}}}}}

   :uberjar {:omit-source true
             :aot :all})
