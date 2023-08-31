;; Copyright (C) 2014 Clark & Parsia
;; Copyright (C) 2014 Paula Gearon
;;
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;      http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns stardog.core
  (:require [stardog.values :as values])
  (:import [com.complexible.stardog.api
            Connection ConnectionPool ConnectionPoolConfig ConnectionConfiguration
            Query ReadQuery]
           [clojure.lang IFn]
           [java.util Map List]
           [com.complexible.common.base CloseableIterator]
           [com.stardog.stark Values Namespace]
           [com.stardog.stark.query SelectQueryResult GraphQueryResult BindingSet Binding]
           [com.stardog.stark.impl StatementImpl]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Connection management

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
    [{:keys [db ^String user ^String pass url server reasoning]}]
    (let [config (ConnectionConfiguration/to db)]
      (when user (.credentials config user pass))
      (when-let [^String server-url (or url server)]
        (.server config server-url))
      (when reasoning (.reasoning config reasoning))
      (.connect config)))
  String
  (connect [cs] (ConnectionConfiguration/at cs)))


(defn make-datasource
  "Creates a Stardog datasource, i.e. ConnectionPool"
  [db-spec]
  (let [{:keys [^String url ^String user ^String pass ^String db
                ^int max-idle ^int min-pool ^int max-pool ^boolean reasoning]} db-spec
        con-config (-> (ConnectionConfiguration/to db)
                       (.server url)
                       (.credentials user pass)
                       (.reasoning reasoning))

        pool-config (-> (ConnectionPoolConfig/using con-config)
                        (.minPool min-pool)
                        (.maxIdle max-idle)
                        (.maxPool max-pool))
        pool (.create pool-config)]
    {:ds pool}))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility functions for marshalling API calls

(defn binding->map
  "Converts a BindingSet into a map."
  [^IFn key-fn ^IFn value-fn ^BindingSet mb]
  (into {} (map (fn [^Binding b] [(key-fn (.name b)) (value-fn (.value b))]))
        (iterator-seq (.iterator mb))))

(defn statement->map
  "Converts a Statement into a map."
  [^IFn value-fn ^StatementImpl mb]
  (vector (value-fn (.subject mb))
          (value-fn (.predicate mb))
          (value-fn (.object mb))))

(defn key-map-results
  "Converts a Iteration of bindings into a seq of keymaps."
  [^IFn keyfn ^IFn valfn ^SelectQueryResult results]
  (let [mapper (partial binding->map keyfn valfn)
        realized-results (into [] (map mapper) (iterator-seq results))
        variables (map keyfn (.variables results))]
    (.close results)
    (vary-meta realized-results assoc :variables variables)))

(defn vector-map-results
  "Converts a Graph of statements into a seq of vectors."
  [^IFn valfn ^CloseableIterator results]
  (let [mapper (partial statement->map valfn)
        realized-results (into [] (map mapper) (iterator-seq results))]
    (.close results)
    realized-results))

(defprotocol ClojureResult
  (clojure-data* [results keyfn valfn]
    "Typed dispatched conversion of query results into Clojure data"))

(extend-protocol ClojureResult
  GraphQueryResult
  (clojure-data* [results keyfn valfn]
    (let [namespaces (into {}
                           (map (fn [^Namespace ns] [(.prefix ns) (.iri ns)]))
                           (iterator-seq (.. results namespaces iterator)))]
      (vary-meta (vector-map-results valfn results) assoc :namespaces namespaces)))
  SelectQueryResult
  (clojure-data* [results keyfn valfn] (key-map-results keyfn valfn results))
  nil
  (clojure-data* [results _ valfn] results)
  Boolean
  (clojure-data* [results _ valfn] results))

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
      (when reasoning (.reasoning rq (boolean reasoning)))
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
                   [reasoning a] (check-arg #(or (true? %) (false? %)) a)
                   [converter a] (check-arg fn? a)
                   [key-converter [limit offset]] (check-arg fn? a)]
               (->> {:base base :parameters params :reasoning reasoning
                     :limit limit :offset offset
                     :converter converter :key-converter key-converter}
                    (filter second)
                    (into {})))))

(defn- order-results [results]
  (let [{:keys [^List variables] :as metadata} (meta results)
        order-result-set (fn [result-set]
                           (into (sorted-map-by (fn [binding1 binding2]
                                                  (compare
                                                    (.indexOf variables binding1)
                                                    (.indexOf variables binding2))))
                                 result-set))]
    (if (not-empty variables)
      (-> (map order-result-set results)
          (with-meta metadata))
      results)))

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
   - reasoning: boolean true/false for reasoning, or not
   - converter: A function to convert returned values with (Function).
   - key-converter: A function to convert returned binding names with (Function).
   - limit: The limit for the result. Must be present to use offset (integer).
   - offset: The offset to start the result (integer).
   - ordered?: Preserve the order of the variable bindings present in the SELECT clause"
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.select connection text %) #(.select connection text) args)]
    (cond-> (execute* q args)
      (get-in args [:parameters :ordered?]) (order-results))))


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

(defn update!
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
  "Inserts a statement (subject, predicate, object) represented as a 3 item vector.
  If a graph URI is specified, the statements will be added to the named graph."
  ([^Connection connection triple-list]
   (insert! connection triple-list Values/DEFAULT_GRAPH))
  ([^Connection connection triple-list graph-uri]
   (when (< (count triple-list) 3) (throw (IllegalArgumentException. "triple-list must have 3 elements")))
   (let [adder (.add connection)
         subj (-> (first triple-list) (values/as-uri) (values/convert))
         pred (-> (second triple-list) (values/as-uri) (values/convert))
         obj  (-> (nth triple-list 2) (values/convert))
         context (if (instance? com.stardog.stark.impl.IRIImpl graph-uri)
                   graph-uri
                   (values/convert (values/as-uri graph-uri)))]
     (.statement adder (StatementImpl. subj pred obj context)))))

(defn remove!
  "Remove a statement from the database; nil's can be used in any position to
  indicate a wildcard matching anything in that position, thereby removing multiple statements.
  If a graph URI is specified, all statements matching the given SPO pattern will be removed
  from the named graph."
  ([^Connection connection triple-list]
   (remove! connection triple-list Values/DEFAULT_GRAPH))
  ([^Connection connection triple-list graph-uri]
   (let [remover (.remove connection)
         subj (when (some? (first triple-list)) (-> triple-list first (values/as-uri) (values/convert)))
         pred (when (some? (second triple-list)) (-> triple-list second (values/as-uri) (values/convert)))
         obj  (when (some? (nth triple-list 2 nil)) (-> (nth triple-list 2) (values/convert)))
         context (if (instance? com.stardog.stark.impl.IRIImpl graph-uri)
                   graph-uri
                   (values/convert (values/as-uri graph-uri)))]
     (.statements remover subj pred obj (into-array com.stardog.stark.Resource [context])))))

(defn remove-named-graph!
  "Remove the named graph and all the statements within from the database. If no graph URI is provided
  this will remove the default graph (no context). If you want to remove everything in the database
  regardless of context, use remove-all!."
  ([^Connection connection]
   (remove! connection Values/DEFAULT_GRAPH))
  ([^Connection connection graph-uri]
   (let [remover (.remove connection)
         context (if (instance? com.stardog.stark.impl.IRIImpl graph-uri)
                   graph-uri
                   (values/convert (values/as-uri graph-uri)))]
     (.context remover context))))

(defn remove-all!
  "Delete the entire contents of the database."
  ([^Connection connection]
   (let [remover (.remove connection)]
     (.all remover))))

(defn add-ns!
  "Adds a namespace prefix"
  [^Connection connection ^String prefix ^String rdf-ns]
  (let [ns-api (.namespaces connection)]
    (.add ns-api prefix rdf-ns)))

(defn remove-ns!
  "Removes a namespace prefix"
  [^Connection connection ^String prefix]
  (let [ns-api (.namespaces connection)]
    (.remove ns-api prefix)))

(defn list-namespaces
  "Lists configured namespaces in the database"
  [^Connection connection]
  (let [ns-api (.namespaces connection)]
    (iterator-seq (.iterator ns-api))))

(defn transact
  "(transact pool (something con ..))
  Executes a function over a connection pool and transaction"
  [datasource func]
  (let [^ConnectionPool connection-pool (:ds datasource)
        ^Connection conn (.obtain connection-pool)
        _ (.begin conn)]
    (try
      (let [result (func conn)]
        (.commit conn)
        result)
      (catch Throwable t
        (.rollback conn))
      (finally
        (.release connection-pool conn)))))


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
  "(with-connection-pool [con datasource] .. con, body ..)
   Evaluates body in the context of an active connection"
  [bindings & body]
  (assert-args
   (vector? bindings) "a vector for its binding"
   (even? (count bindings)) "an even number of forms in binding vector")
  (cond
    (empty? bindings) `(do ~@body)
    (symbol? (bindings 0))
    `(let [datasource# ~(second bindings)
           ^ConnectionPool connection-pool# (:ds datasource#)]
       (let
           [~(first bindings) (.obtain connection-pool#)]
         (try
           ~@body
           (finally
             (.release connection-pool# ~(first bindings))))))
    :else (throw (IllegalArgumentException.
                  "with-pool only allows Symbols in bindings"))))
