(ns state-flow.assertions.matcher-combinators
  (:require [cats.core :as m]
            [matcher-combinators.core :as matcher-combinators]
            [matcher-combinators.test] ;; to register clojure.test assert-expr for `match?`
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.state :as state]))

(defn ^:private match-probe
  "Internal use only.

  Returns the right value returned by probe/probe."
  [state matcher params]
  (m/fmap (comp :value last)
          (probe/probe state
                       #(matcher-combinators/match? (matcher-combinators/match matcher %))
                       params)))

(defmacro match?
  "Builds a state-flow assertion using matcher-combinators.

  - expected can be a literal value or a matcher-combinators matcher
  - actual can be a literal value, a primitive step, or a flow
  - params is an optional map supporting:
    - :times-to-try optional, default 1
    - :sleep-time   optional, millis to wait between tries, default 200

  Given (= :times-to-try 1), match? will evaluate `actual` just once.

  Given (> :times-to-try 1), match? will use `state-flow-probe/probe` to
  retry up to :times-to-try times, waiting :sleep-time between each try,
  and stopping when `actual` produces a value that matches `expected`.

  See `state-flow.probe/probe` for more info"
  [expected actual & [{:keys [times-to-try]
                       :or {times-to-try 1}
                       :as params}]]
   ;; description is here to support the
   ;; deprecated cljtest/match? fn.  Undecided
   ;; whether we want to make it part of the API.
   ;; caller-meta is definitely not part of the API.
  (let [params* (merge {:description "match?"
                        :caller-meta (meta &form)
                        :times-to-try 1}
                       params)]
    (core/flow*
     {:description (:description params*)
      :caller-meta (:caller-meta params*)}
      ;; Nesting m/do-let inside a call the function core/flow* is
      ;; a bit ugly, but it supports getting the correct line number
      ;; information from core/current-description.
     `(m/do-let
       [flow-desc# (core/current-description)
        actual#    (if (> (:times-to-try ~params*) 1)
                     (#'match-probe (state/ensure-step ~actual) ~expected ~params*)
                     (state/ensure-step ~actual))]
        ;; TODO: (dchelimsky, 2020-02-11) we plan to decouple
        ;; assertions from reporting in a future release. Remove this
        ;; next line when that happens.
       (state/wrap-fn #(~'clojure.test/testing flow-desc# (~'clojure.test/is (~'match? ~expected actual#))))
       (state/return actual#)))))