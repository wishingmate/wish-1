(defproject wish "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.329"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.reader "1.2.2"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.5"]
                 [secretary "1.2.3"]
                 [re-pressed "0.2.2"]

                 [kibu/pushy "0.3.8"]
                 [cljs-ajax "0.7.3"]

                 ; simpler forms
                 [reagent-forms "0.5.42"]

                 ; sheet-specific inline css
                 [cljs-css-modules "0.2.1"]

                 ; ::inject/sub cofx (for subscriptions in event handlers)
                 [re-frame-utils "0.1.0"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-less "1.7.5"]
            [lein-npm "0.6.2"]]

  ; npm is only needed for installing test dependencies
  :npm {:devDependencies [[karma "2.0.3"]
                          [karma-cljs-test "0.1.0"]
                          [karma-chrome-launcher "2.2.0"]]}

  :doo {:paths {:karma "./node_modules/karma/bin/karma"}}

  :min-lein-version "2.5.3"

  :source-paths ["src/clj" "src/cljc"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]
             :nrepl-port 7002
             :server-ip "0.0.0.0"
             :ring-handler wish.dev-server/http-handler
             :nrepl-middleware
             [cemerick.piggieback/wrap-cljs-repl cider.nrepl/cider-middleware]}

  :less {:source-paths ["less"]
         :target-path  "resources/public/css"}

  ;; :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  :aliases {"dev" ["do" "clean"
                        ["pdo" ["less" "auto"]
                               ["figwheel" "dev"]]]
            "build" ["with-profile" "+prod,-dev" "do"
                          ["clean"]
                          ["cljsbuild" "once" "min"]
                          ["less" "once"]]
            "test" ["doo" "chrome" "test" "once"]}

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.9.10"]
                   [day8.re-frame/re-frame-10x "0.3.3-react16"]
                   [day8.re-frame/tracing "0.5.1"]
                   [figwheel-sidecar "0.5.16"]
                   ;; [cider/piggieback "0.3.6"]
                   [com.cemerick/piggieback "0.2.2"]

                   [ring "1.4.0"]
                   [ring/ring-defaults "0.2.0"]
                   [compojure "1.5.0"]]

    :source-paths ["src/cljs"]

    :plugins      [[lein-figwheel "0.5.16"]
                   [lein-doo "0.1.10"]
                   [lein-pdo "0.1.1"]]}

   :prod { :dependencies [[day8.re-frame/tracing-stubs "0.5.1"]]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs" "dev/cljs"]
     :figwheel     {:on-jsload "wish.core/mount-root"}
     :compiler     {:main                 wish.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload
                                           day8.re-frame-10x.preload]
                    :closure-defines      {"re_frame.trace.trace_enabled_QMARK_" true
                                           "day8.re_frame.tracing.trace_enabled_QMARK_" true}
                    :external-config      {:devtools/config {:features-to-install :all}}
                    }}

    {:id           "min"
     :source-paths ["src/cljs" "prod/cljs"]
     :compiler     {:main            wish.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false

                    :externs ["externs/gapi.js"
                              "externs/wish.js"]}}

    {:id           "test"
     :source-paths ["src/cljs" "dev/cljs" "test/cljs"]
     :compiler     {:main          wish.runner
                    :output-to     "resources/public/js/compiled/test.js"
                    :output-dir    "resources/public/js/compiled/test/out"
                    :optimizations :none}}
    ]}

  )
