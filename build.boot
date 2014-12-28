(set-env! :source-paths #{"src" "task"})

(task-options! pom {:project 'experimental-db
                    :version "0.0.1"})

(require '[exdb.node :refer :all])

(deftask build
  "Build project"
  []
  (comp (pom) (jar) (install)))
