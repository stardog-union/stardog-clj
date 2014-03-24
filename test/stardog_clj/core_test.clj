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

(ns stardog-clj.core-test
  (:use stardog-clj.core
        midje.sweet)
  (:import (com.complexible.stardog.api  Connection
                                          ConnectionPool
                                          ConnectionPoolConfig
                                          ConnectionConfiguration)
            (org.openrdf.model.impl URIImpl)))


(facts "About stardog connection pool handling"
       (fact "create-db-spec returns a valid map"
             (create-db-spec "testdb" "snarl://localhost:5820/" "admin" "admin") =>
                             {:url "snarl://localhost:5820/" :dbname "testdb" :pass "admin" :user "admin" :max-idle 100 :max-pool 200 :min-pool 10}))

;(def my-pool (make-datasource (create-db-spec "testdb" "snarl://localhost:5820/" "admin" "admin")))

;(println my-pool)

;(def r (query my-pool "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 2" {"s" (URIImpl. "urn:test2")} ))


;(db-transact my-pool (fn [con] (println con)))


;(with-database [con my-pool]
;  (println con))

