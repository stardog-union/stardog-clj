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
  (:use stardog.values
        midje.sweet)
   (:import [org.openrdf.model URI Literal BNode Value]
           [org.openrdf.model.impl CalendarLiteralImpl
                                   IntegerLiteralImpl
                                   LiteralImpl
                                   NumericLiteralImpl
                                   URIImpl]
           [java.util Date GregorianCalendar]
           [javax.xml.datatype DatatypeConfigurationException
                               DatatypeFactory
                               XMLGregorianCalendar ]))

(facts "Converting from Clojure data structures to RDF"
       (fact "Converting a URI"
             (type (convert (java.net.URI. "http://test.com"))) => URIImpl )
       (fact "Converting an Integer"
             (type (convert (Integer. 1))) => NumericLiteralImpl)
       (fact "Converting a String"
             (type (convert "test")) => LiteralImpl)
       (fact "Converting a java.util.Date"
             (type (convert (Date. ))) => CalendarLiteralImpl))

(facts "URI Creation"
       (fact "Creating a URI from a String"
             (as-uri "http://test.com") => (java.net.URI. "http://test.com")))
