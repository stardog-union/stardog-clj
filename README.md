# stardog-clj

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

[![Clojars Project](http://clojars.org/stardog-clj/latest-version.svg)](http://clojars.org/stardog-clj)


Stardog-clj - Clojure language bindings to use to develop apps with the [Stardog Graph / RDF Database](http://stardog.com).

![Stardog](http://docs.stardog.com/img/sd.png)


## Usage


Out of the box, Stardog provides a Java API, SNARL, for communicating with the Stardog database.  SNARL is a connection oriented API, with both a connection and connection pool available, similar to JDBC.  Queries can be made using the SPARQL query language, or by using various SNARL APIs for navigating the structure of the data. Stardog-clj provides APIs to do all of these functions using idiomatic clojure style of programming.  The API builds upon itself, being able to wrap usage with connection pools, create connections directly, etc.


### Query Execution

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
```clojure
(with-open [c (connect {:db "my-database" :server "snarl://localhost"})]
  (with-transaction [c]
    (insert! c ["urn:a:subject" "urn:a:predicate" "an object"])
```

There are wrappers for:
 * connect
 * query
 * update
 * ask
 * graph
 * adder

Most query options are available for configuring as keys in the parameter map. When requesting
reasoners, use strings or keywords.

### Query Results

Results from SPARQL queries are lazy sequences of bindings from variable names to values.
By default, variable names are converted to keywords, and values are left untouched. This can
be changed by providing functions for the :key-converter and :converter parameters.

Graph results are the same as query results, with namespaces attached as metadata on the entire
sequence (not on sub-sequences).

### Transactions

While there are no update api wrappers yet, there is a macro for dealing with transactions:

```clojure
(with-open [c (connect {:db "my-database" :server "snarl://localhost"})]
  (with-transaction [c]
    (.. c
        (add)
        (io)
        (format RDFFormat/N3)
        (stream (input-stream "data.n3")))))
```

Note: the with-open macro closes a connection, which is not recommended for using the Stardog connection pool.  In lieu of with-open, there is a with-connection-pool macro available, that provides appropriate connection pool resource handling.

## Building

To build stardog-clj, you must perform the following steps:

1. Download stardog from [Stardog.com](http://www.stardog.com)
2. Run "mavenInstall" from the stardog-2.1.2/bin folder
3. Run "stardog-admin server start"
4. Run "stardog-admin db create -n testdb path/to/data/University0_0.owl path/to/data/lubmSchema.owl"
5. You can now run lein compile, use the lein repl, and run lein midje to perform the tests



## License

Copyright 2014 Clark & Parsia

Copyright 2014 Paul Gearon

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

* [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
