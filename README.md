# stardog-clj

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

[![Clojars Project](http://clojars.org/stardog-clj/latest-version.svg)](http://clojars.org/stardog-clj)


Stardog-clj - Clojure language bindings to use to develop apps with the [Stardog Graph / RDF Database](http://stardog.com).

![Stardog](http://stardog.com/img/stardog.png)


## Usage

To use stardog-clj, follow these simple steps:

1. Download [Stardog](http://stardog.com), and unzip it
2. In your application, add the stardog-clj dependency to your project.clj file, or equivalent build tool.  For example, `[stardog-clj "7.0.0"]`
3. In your application, create a database specification `(create-db-spec database "http://localhost:5820/" "admin" "admin" true)`
4. You can use this specification to make a connection pool with `(make-datasource spec)`
5. Use `(with-connection-pool [conn datasource])` to start using the connection pool

Out of the box, Stardog provides a Java API, SNARL, for communicating with the Stardog database.  SNARL is a connection oriented API, with both a connection and connection pool available, similar to JDBC.  Queries can be made using the SPARQL query language, or by using various SNARL APIs for navigating the structure of the data. Stardog-clj provides APIs to do all of these functions using idiomatic clojure style of programming.  The API builds upon itself, being able to wrap usage with connection pools, create connections directly, etc.


### Query Execution

All Stardog queries are executed given a connection, a query string, and an optional map of parameters.  The connection can be created using the `connnect` function, or with the `with-connection-pool` macro.  Connection configuration is a simple map, and there is a helper function for creating database specs, using the `make-datasource` function.

```clojure
=> (use 'stardog.core)
=> (def c (connect {:db "my-database" :server "snarl://localhost"}))
=> (def results (query c "select ?n { .... }"))
=> (take 5 results)
({:n #<StardogURI http://mulgara.org/math#2>} {:n #<StardogURI http://mulgara.org/math#3>} {:n #<StardogURI http://mulgara.org/math#5>} {:n #<StardogURI http://mulgara.org/math#7>} {:n #<StardogURI http://mulgara.org/math#11>})

=> (def string-results (query c "select ?n { .... }" {:converter str}))
=> (take 5 string-results)
({:n "http://mulgara.org/math#2"} {:n "http://mulgara.org/math#3"} {:n "http://mulgara.org/math#5"} {:n "http://mulgara.org/math#7"} {:n "http://mulgara.org/math#11"})
```

### Insert triples

Stardog-clj includes easy to use functions for adding triples or removing triples from the Stardog database.  The shape of the data used in the API is a vector of three elements, confirming to the subject, predicate, object "triple", also known as an entity attribute value model.

```clojure
(with-open [c (connect {:db "my-database" :server "snarl://localhost"})]
  (with-transaction [c]
    (insert! c ["urn:a:subject" "urn:a:predicate" "an object"])
```

There are wrappers for:
 * `query` for the SPARQL SELECT query
 * `update!` for the SPARQL 1.1 UPDATE queries
 * `ask` for running SPARQL ASK queries
 * `graph` for running SPARQL CONSTRUCT queries
 * `insert!` and `remove!` for the SNARL adder and remover to add or remove RDF statements
 * `connect` for connection handling, including connection pools
 * namespace manipulation, for adding and removing server mananaged namespace prefixes

Most query options are available for configuring as keys in the parameter map. When requesting reasoners, use strings or keywords.

### Query Results

Results from SPARQL queries are lazy sequences of bindings from variable names to values.
By default, variable names are converted to keywords, and values are left untouched. This can
be changed by providing functions for the :key-converter and :converter parameters.

Graph results are the same as query results, with namespaces attached as metadata on the entire
sequence (not on sub-sequences).

### Transactions

There is a macro for dealing with transactions, where you can directly use the connection to perform bulk add operations, such as below, or for putting multiple `insert!` and `remove!` calls into a single transaction.

```clojure
(with-open [c (connect {:db "my-database" :server "snarl://localhost"})]
  (with-transaction [c]
    (.. c
        (add)
        (io)
        (format RDFFormat/N3)
        (stream (input-stream "data.n3")))))
```

Note: the usual `with-open` macro closes a connection, which is not recommended for using the Stardog connection pool.  In lieu of with-open, there is a `with-connection-pool` macro available, that provides appropriate connection pool resource handling.

## Building the library

To build stardog-clj, you must perform the following steps:

1. Download stardog from [Stardog.com](http://www.stardog.com)
2. Run "stardog-admin server start"
3. Run "stardog-admin db create -n testdb path/to/data/University0_0.owl path/to/data/lubmSchema.owl"
4. You can now run lein compile, use the lein repl, and run lein midje to perform the tests

The test suite does run with the assumption there is a Stardog database server running.


## License

Copyright 2014, 2015, 2016, 2017, 2018, 2019 Stardog Union

Copyright 2014 Paula Gearon

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

* [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
