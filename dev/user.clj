(ns user
  (:require [integrant.repl :as ig-repl :refer [go halt reset reset-all]]
            [integrant.repl.state :refer [system]]
            [starter.service :refer [ig-config]]))

(ig-repl/set-prep! (constantly ig-config))
