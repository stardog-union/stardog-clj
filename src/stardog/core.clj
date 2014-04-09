 ; Copyright (C) 2014 Clark & Parsia
 ; Copyright (C) 2014 Paul Gearon
 ;
 ;
 ; Licensed under the Apache License, Version 2.0 (the "License");
 ; you may not use this file except in compliance with the License.
 ; You may obtain a copy of the License at
 ;
 ;      http://www.apache.org/licenses/LICENSE-2.0
 ;
 ; Unless required by applicable law or agreed to in writing, software
 ; distributed under the License is distributed on an "AS IS" BASIS,
 ; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ; See the License for the specific language governing permissions and
 ; limitations under the License.

(ns stardog.core
   (:require [clojure.string :as str]
             [stardog.values :as values])
   (:import [com.complexible.stardog.api  Connection
                                          ConnectionPool
                                          ConnectionPoolConfig
                                          ConnectionConfiguration]
            [clojure.lang IFn]
            [java.util Map]
            [com.complexible.common.openrdf.model Graphs]
            [com.complexible.stardog.api ConnectionConfiguration Connection Query ReadQuery]
            [com.complexible.stardog.reasoning.api ReasoningType]
            [org.openrdf.query TupleQueryResult GraphQueryResult BindingSet Binding]
            [org.openrdf.model URI Literal BNode]
            [org.openrdf.model.impl StatementImpl]
            [info.aduna.iteration Iteration]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Connection management

(defn reasoning-type
  ^ReasoningType [r]
  (let [t (str/upper-case (name r))]
    (ReasoningType/valueOf t)))

(defn create-db-spec
  "Helper function to create a dbspec with sensible defaults for nontypical parameters"
  [db url user pass reasoning]
  {:url url
   :db db
   :pass pass
   :user user
   :max-idle 100
   :max-pool 200
   :min-pool 10
   :reasoning reasoning})

(defprotocol Connectable
  (connect [c] "Creates a connection with the given parameters"))

(extend-protocol Connectable
  java.util.Map
  (connect
    [{:keys [db user pass url reasoning]}]
    (let [config (ConnectionConfiguration/to db)]
      (when user (.credentials config user pass))
      (when url (.server config url))
      (when reasoning (.reasoning config (reasoning-type reasoning)))
      (.connect config)))
  String
  (connect [cs] (ConnectionConfiguration/at cs)))


(defn make-datasource
  "Creates a Stardog datasource, i.e. ConnectionPool"
  [db-spec]
  (let [{:keys [url user pass db
                max-idle min-pool max-pool reasoning]} db-spec
        con-config (-> (ConnectionConfiguration/to db )
                       (.server url)
                       (.credentials user pass)
                       (.reasoning (reasoning-type reasoning))
                       )
        pool-config (-> (ConnectionPoolConfig/using con-config)
                        (.minPool min-pool)
                        (.maxIdle max-idle)
                        (.minPool min-pool))
        pool (.create pool-config)]
    {:ds pool}))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility functions for marshalling API calls

(defn as-map
  "Converts a BindingSet into a map."
  [^IFn key-fn ^IFn value-fn ^BindingSet mb]
  (into {} (map (fn [^Binding b] [(key-fn (.getName b)) (value-fn (.getValue b))])
                (iterator-seq (.iterator mb)))))

;; From http://stackoverflow.com/questions/9225948/how-do-turn-a-java-iterator-like-object-into-a-clojure-sequence
;; Leaving both as-seq and iteration->seq, lazy and not lazy respectively
;; until proper benchmarking can be done of the combination of Stardog result set processing and Clojure sequence APIs
(defn as-seq
  "Converts an Iteration into a lazy-seq"
  [^Iteration i]
  (if-not (.hasNext i) nil (cons (.next i) (lazy-seq (as-seq i)))))

(defn iteration->seq
   "Converts Iteration into a seq"
  [iteration]
  (seq
    (reify java.lang.Iterable
      (iterator [this]
         (reify java.util.Iterator
           (hasNext [this] (.hasNext iteration))
           (next [this] (.next iteration))
           (remove [this] (.remove iteration)))))))


(defn key-map-results
  "Converts a Iteration of bindings into a seq of keymaps."
  [^IFn keyfn ^IFn valfn ^Iteration results]
  (let [mapper (partial as-map keyfn valfn)]
    (map mapper (as-seq results))))

(defprotocol ClojureResult
  (clojure-data* [results keyfn valfn]
                 "Typed dispatched conversion of query results into Clojure data"))

(extend-protocol ClojureResult
  GraphQueryResult
  (clojure-data* [results keyfn valfn]
    (let [namespaces (into {} (.getNamespaces results))]
      (with-meta {:namespaces namespaces} (key-map-results keyfn valfn results))))
  TupleQueryResult
  (clojure-data* [results keyfn valfn] (key-map-results keyfn valfn results))
  Boolean
  (clojure-data* [results _ valfn] (valfn results)))

(defn clojure-data
  "Converts query results into Clojure data. Optionally uses functions for interpreting
   names and value bindings in results."
  ([results] (clojure-data* results keyword values/standardize))
  ([results keyfn valfn] (clojure-data* results keyfn valfn)))

(defn execute* [^Query q {:keys [key-converter converter]
                         :or {key-converter keyword converter values/standardize}}]
  (clojure-data (.execute q) key-converter converter))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Query APIs

(defn configure-query
  "Configures a query is the valid parameters for that type of query"
  [^Query q {:keys [parameters reasoning limit offset dataset]}]
  (doseq [[k v] parameters] (.parameter q (name k) v))
  (when dataset (.dataset q dataset))
  (when (instance? ReadQuery q)
    (let [^ReadQuery rq q]
      (when reasoning (.reasoning rq (reasoning-type reasoning)))
      (when limit (.limit rq limit))
      (when offset (.offset rq offset))))
  q)

(defn create-query
  "Creates a query using a map of optional arguments.
   new-with-base: Function that creates the query with a base URI.
   new-without-base: Function that creates the query without a base URI.
   args: A map containing any of the following: base, parameters, reasoning, limit, offset"
  ^Query
  [^IFn new-with-base ^IFn new-without-base ^Map {:keys [base] :as args}]
  (let [q (if base (new-with-base base) (new-without-base))]
    (configure-query q args)))

(def query-keys #{:base :parameters :reasoning :limit :offset :converter :key-converter :dataset})

(defn check-arg [pred [f & r :as a]] (if (pred f) [f r] [nil a]))

(defn convert-to-map
  "Converts an arguments array into a map. The arguments are either positional,
   named, or already in map form. This function is a fixpoint."
  [[f & r :as args]]
  (cond
    (and (map? f)
         (= 1 (count args))
         (every? query-keys (keys f))) f
    (keyword? f) (apply map args)
    ;; walk down the arguments and pull them out positionally
    :default (let [[base a] (check-arg string? args)
                   [params a] (check-arg map? a)
                   [reasoning a] (check-arg #(or (string? %) (keyword? %)) a)
                   [converter a] (check-arg fn? a)
                   [key-converter [limit offset]] (check-arg fn? a)]
               (->> {:base base :parameters params :reasoning reasoning
                     :limit limit :offset offset
                     :converter converter :key-converter key-converter}
                    (filter second)
                    (into {})))))


(defn query
  "Executes a query and returns results.
   When constructing a query from text, the parameters are:
   - connection: The connection to query over (required).
   - text: The text of the connection (String - required).
   Remaining argument are optional, and may be positional args,
   a map of args, or named args. Mapped and named args use the keys:
   - base, parameters, reasoning, limit, offset, converter, key-converter
   Positional arguments are in order:
   - base: The base URI for the query (String).
   - parameters: A parameter map to bind parameters in the query (Map).
   - reasoning: The type of reasoning to use with the query (String/keyword).
   - converter: A function to convert returned values with (Function).
   - key-converter: A function to convert returned binding names with (Function).
   - limit: The limit for the result. Must be present to use offset (integer).
   - offset: The offset to start the result (integer)."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.select connection text %) #(.select connection text) args)]
    (execute* q args)))


(defn ask
  "Executes a boolean query.
  Optional parameters may be provided as a map or named parameters.
  Parameter names are:
  - base, parameters, reasoning, limit, offset, converter, key-converter."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.ask connection text %) #(.ask connection text) args)]
    (execute* q args)))

(defn graph
  "Executes a graph query.
  Optional parameters may be provided as a map or named parameters.
  Parameter names are:
  - base, parameters, reasoning, limit, offset, converter, key-converter."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.graph connection text %) #(.graph connection text) args)]
    (execute* q args)))

(defn update
  "Executes an update operation.
  Optional parameters may be provided as a map or named parameters.
  Parameter names are:
  - base, parameters, reasoning, converter."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.update connection text %) #(.update connection text) args)]
    (execute* q args)))

(defn execute
  "Executes a query that has already been created and configured.
   Valid parameters are key-converter and converter. Query configuration
   parameters are ignored."
  [^Query q & args]
  (execute* q (convert-to-map args)))


(defn insert!
  "Inserts a statement (subject, predicate, object) represented as a 3 item vector"
  [^Connection connection triple-list]
  (when (< (count triple-list) 3) (throw (IllegalArgumentException. "triple-list must have 3 elements")))
  (let [adder (.add connection)
          subj (-> (first triple-list) (values/as-uri) (values/convert) )
          pred (-> (second triple-list) (values/as-uri) (values/convert))
          obj  (-> (nth triple-list 2) (values/convert))]
      (.statement adder (StatementImpl. subj pred obj))))

(defn remove!
  "Removes a statements (subject, predicate, object) represented as a 3 item vector"
  [^Connection connection triple-list]
  (when (< (count triple-list) 3) (throw (IllegalArgumentException. "triple-list must have 3 elements")))
  (let [remover (.remove connection)
          subj (-> (first triple-list) (values/as-uri) (values/convert) )
          pred (-> (second triple-list) (values/as-uri) (values/convert))
          obj  (-> (nth triple-list 2) (values/convert))]
      (.statement remover (StatementImpl. subj pred obj))))


(defn transact
  "(transact pool (something con ..))
  Executes a function over a connection pool and transaction"
  [pool func]
  (let [conn (.obtain (:ds pool))
        _ (.begin conn)]
     (try
      (let [result (func conn)]
        (.commit conn)
        result)
     (catch Throwable t
       (.rollback conn))
     (finally
        (.release (:ds pool) conn)))))


(defmacro assert-args
  "Duplicates the functionality of the private clojure.core/assert-args"
  [& pairs]
  `(do (when-not ~(first pairs)
         (throw (IllegalArgumentException.
                  (str (first ~'&form) " requires " ~(second pairs) " in " ~'*ns* ":" (:line (meta ~'&form))))))
    ~(let [more (nnext pairs)]
       (when more
         (list* `assert-args more)))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Resource evaluation macros

(defmacro with-transaction
  "(with-transaction [connection...] body)
  Executes the body with a transaction on each of the connections. At completion of the body
  the transaction is committed. If the body fails due to exception, the transaction is rolled back.
  This macro intentionally restricts connections to be symbols, to encourage them to be
  bindings in with-open."
  [connections & body]
  (assert-args
    (vector? connections) "a vector for its connections"
    (every? symbol? connections) "symbols for all connections")
  (let [begins (for [c connections] `(.begin ~c))
        rev (reverse connections)
        commits (for [c rev] `(.commit ~c))
        rollbacks (for [c rev] `(.rollback ~c))]
    `(do
       ~@begins
       (try
         ~@body
         ~@commits
         (catch Throwable t#
           ~@rollbacks
           (throw t#))))))

(defmacro with-connection-tx
  "(with-connection-tx binding-forms body)
   Establishes a connection and a transaction to execute the body within."
  [bindings & body]
  (assert-args
    (vector? bindings) "a vector for its binding"
    (even? (count bindings)) "an even number of forms in binding vector")
  (cond
    (empty? bindings) `(do ~@body)
    (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                              (try
                                (with-transaction [~(bindings 0)]
                                  (with-connection-tx ~(subvec bindings 2) ~@body))
                                (finally
                                  (.close ~(bindings 0)))))
    :else (throw (IllegalArgumentException.
                   "with-connection-tx only allows Symbols in bindings"))))

(defmacro with-connection-pool
  "(with-connection-pool [con pool] .. con, body ..)
   Evaluates body in the context of an active connection"
  [bindings & body]
  (assert-args
   (vector? bindings) "a vector for its binding"
   (even? (count bindings)) "an even number of forms in binding vector")
  (cond
   (empty? bindings) `(do ~@body)
   (symbol? (bindings 0))
  `(let [db-spec# ~(second bindings)]
     (let [~(first bindings) (.obtain (:ds db-spec#))]
       (try
       ~@body
       (finally
        (.release (:ds db-spec#) ~(first bindings))))))
   :else (throw (IllegalArgumentException.
                   "with-pool only allows Symbols in bindings"))))











