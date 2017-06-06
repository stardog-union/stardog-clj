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

(ns stardog.test.values
  (:use [stardog.values]
        [midje.sweet])
   (:import [org.openrdf.model URI Literal BNode Value]
            [org.openrdf.model.impl CalendarLiteral
                                    IntegerLiteral
                                    LiteralImpl
                                    NumericLiteral
                                    URIImpl]
            [com.complexible.common.rdf.model StardogIntLiteral
					      StardogStringLiteral
					      StardogDateTimeLiteral
                                              ]
            [java.util Date GregorianCalendar UUID]
            [javax.xml.datatype DatatypeConfigurationException
                                DatatypeFactory
                                XMLGregorianCalendar]))

(facts "Converting from Clojure data structures to/from RDF"
       (fact "Converting a URI to a URI Impl"
             (type (convert (java.net.URI. "http://test.com"))) => URIImpl )
       (fact "Converting a URI to a URI Impl isomorphic with standardize"
             (standardize (convert (java.net.URI. "http://test.com"))) => (as-uri "http://test.com") )
       (fact "Converting an Integer"
             (type (convert (Integer. 1))) => StardogIntLiteral)
       (fact "Converting an Integer isomorphic with standardize"
             (standardize (convert (Integer. 1))) => (Integer. 1))
       (fact "Converting a String"
             (type (convert "test")) => StardogStringLiteral)
       (fact "Converting a java.util.Date"
             (type (convert (Date.))) => StardogDateTimeLiteral)
       (fact "LiteralImpl to String"
             (standardize (convert "test")) => "test")
       (fact "CalendarImpl to java.util.Date"
             (let [d (Date.)]
             (standardize (convert d)) => d))
       (fact "UUID to UUID"
             (let [u (UUID/randomUUID)]
             (standardize (convert u)) => u))
       )

(facts "URI Creation"
       (fact "Creating a URI from a String"
             (as-uri "http://test.com") => (java.net.URI. "http://test.com")))

