 ; Copyright (C) 2014 Clark & Parsia
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

(ns stardog-clj.core
   (:import (com.complexible.stardog.api  Connection
                                          ConnectionPool
                                          ConnectionPoolConfig
                                          ConnectionConfiguration)))

(defn create-db-spec
  "Helper function to create a dbspec with sensible defaults for nontypical parameters"
  [dbname url user pass]
  {:url url :dbname dbname :pass pass :user user :max-idle 100 :max-pool 200 :min-pool 10})

(defn make-datasource
  "Creates a Stardog datasource, i.e. ConnectionPool"
  [db-spec]
  (let [{:keys [url user pass dbname
                max-idle min-pool max-pool]} db-spec
        con-config (-> (ConnectionConfiguration/to dbname )
                       (.server url)
                       (.credentials user pass)
                       )
        pool-config (-> (ConnectionPoolConfig/using con-config)
                        (.minPool min-pool)
                        (.maxIdle max-idle)
                        (.minPool min-pool))
        pool (.create pool-config)]
    {:ds pool}))



;; From http://stackoverflow.com/questions/9225948/how-do-turn-a-java-iterator-like-object-into-a-clojure-sequence
(defn iteration->seq
   "Converts the Sesame iterable interface into a regular java iterator for use as a clj sequence"
  [iteration]
  (seq
    (reify java.lang.Iterable
      (iterator [this]
         (reify java.util.Iterator
           (hasNext [this] (.hasNext iteration))
           (next [this] (.next iteration))
           (remove [this] (.remove iteration)))))))

(defn query
  "Runs SELECT queries with a datasource, querystring, and (optional) arguments. Arguments are a hashmap,
  with keywords for SPARQL var names"
  ([ds qs]  (query ds qs {}) ) ;; unclear if better than the & syntax
  ([ds qs args]
    (let [conn (.obtain (:ds ds))
          sel-query (.select conn qs)]
     (try
       ;; Handle query parameter setting
       (when (>= (count args) 1)
         (doall (map
          (fn [m]
            (let [[k v] m]
              (.parameter sel-query k v)))
          args)))
       (map (fn [rs]
              (into {} (mapcat
                          (fn [vn]
                            {(keyword vn) (.getValue rs vn)})
                        (.getBindingNames rs))))
       (iteration->seq (.execute sel-query)))
     (finally (.release (:ds ds) conn))))))


(defn db-transact
  "Executes a function over a connection and transaction"
  [db func]
  (let [conn (.obtain (:ds db))
        _ (.begin conn)]
     (try
      (let [result (func conn)]
        (.commit conn)
        result)
     (catch Throwable t
       (.rollback conn))
     (finally
        (.release (:ds db) conn)))))


(defmacro with-database
  "Evaluates body in the context of an active connection
  (with-database [con db]
    ... con ...)"
  [binding & body]
  `(let [db-spec# ~(second binding)]
     (let [~(first binding) (.obtain (:ds db-spec#))]
       (try
       ~@body
       (finally
        (.release (:ds db-spec#) ~(first binding)))))))
















