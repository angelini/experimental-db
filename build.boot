(set-env! :source-paths #{"src" "task"}
          :resource-paths #{"src"}
          :dependencies '[[org.clojure/clojure "1.6.0" :scope "provided"]])

(require '[exdb.boot-node :refer :all])

(deftask build
  "Build project"
  []
  (comp
   (aot :namespace '#{exdb.core})
   (pom :project 'exdb
        :version "0.0.1")
   (uber)
   (jar :main 'exdb.core)))
