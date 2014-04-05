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

(ns stardog.test.core
  (:use stardog.core
        midje.sweet)
   (:import [com.complexible.stardog.api  Connection
                                          ConnectionPool
                                          ConnectionPoolConfig
                                          ConnectionConfiguration]
            [clojure.lang IFn]
            [java.util Map]
            [com.complexible.stardog.api ConnectionConfiguration Connection Query ReadQuery]
            [com.complexible.stardog.reasoning.api ReasoningType]
            [org.openrdf.query TupleQueryResult GraphQueryResult BindingSet Binding]
            [org.openrdf.model URI Literal BNode]
            [info.aduna.iteration Iteration]))


(def test-db-spec (create-db-spec "testdb" "snarl://localhost:5820/" "admin" "admin" "none"))

(facts "About stardog connection pool handling"
       (fact "create-db-spec returns a valid map"
             (create-db-spec "testdb" "snarl://localhost:5820/" "admin" "admin" "none") =>
                             {:url "snarl://localhost:5820/" :db "testdb" :pass "admin" :user "admin" :max-idle 100 :max-pool 200 :min-pool 10 :reasoning "none"})
       (fact "make-datasource creates a map with a connection pool"
             (str (type (:ds (make-datasource (create-db-spec "testdb" "snarl://localhost:5820/" "admin" "admin" "none"))))) =>
             (contains "com.complexible.stardog.api.ConnectionPool")))

(facts "About stardog connection handling"
       (fact "create a stardog connection"
             (.isOpen (connect test-db-spec)) => truthy)
       (fact "close a stardog connection"
             (let [c (connect test-db-spec)]
                   (.close c)
                   (.isOpen c)) => falsey))

(facts "About Stardog SPARQL queries"
       (let [c (connect test-db-spec)
             r (query c "select ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 5")]
         (fact "query results should have 5 elements"
               (count r) => 5)))

(facts "About inserting triples"
       (fact "Insert a vector representing a triple"
             (let [c (connect test-db-spec)]
               (with-transaction [c] (insert! c ["urn:test" "urn:test:clj:prop" "Hello World"]))) => nil))



