# stardog-clj

Stardog-clj bindings, providing an idiomatic clojure interface to the Stardog SNARL API.


## Usage

The Stardog-clj bindings are similar to clojure.java.jdbc:

    (def my-pool (make-datasource (create-db-spec "testdb" "snarl://localhost:5820/" "admin" "admin")))

    (query my-pool "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 2" {"s" (URIImpl. "urn:test2")} ))

    (db-transact my-pool (fn [con] (... use SNARL API directly with a connection and tx ...)))

    (with-database [con my-pool]
         (.... con, but no tx))

## License

Copyright 2014 Clark & Parsia

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

* [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
