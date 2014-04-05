 ; Copyright (C) 2014 Paul Gearon
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

(ns stardog.values
  (:import [org.openrdf.model URI Literal BNode Value]
           [org.openrdf.model.impl CalendarLiteralImpl
                                   IntegerLiteralImpl
                                   LiteralImpl
                                   NumericLiteralImpl
                                   URIImpl]
           [java.util Date GregorianCalendar]
           [javax.xml.datatype DatatypeConfigurationException DatatypeFactory XMLGregorianCalendar ]))

(defmulti typed-value (fn [^Literal v] (str (.getDatatype v))))

(defmethod typed-value "http://www.w3.org/2001/XMLSchema#integer"
  [^Literal v] (.intValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#int"
  [^Literal v] (.intValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#boolean"
  [^Literal v] (.booleanValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#byte"
  [^Literal v] (.byteValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#dateTime"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#time"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#date"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gYearMonth"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gMonthYear"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gYear"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gMonth"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gDay"
  [^Literal v] (-> (.calendarValue v) (.toGregorianCalendar) (.getTime)))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#decimal"
  [^Literal v] (.decimalValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#double"
  [^Literal v] (.doubleValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#float"
  [^Literal v] (.floatValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#long"
  [^Literal v] (.longValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#short"
  [^Literal v] (.shortValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#string"
  [^Literal v] (.getLabel v))
(defmethod typed-value :default
  [^Literal v]
  (let [lang (.getLanguage v)
        label (.getLabel v)]
    (if lang
      {:lang lang :value label}
      (if-let [dt (.getDatatype v)]
        {:datatype dt :value label}
        label))))

(defprotocol RDFConverter
  (convert [v] "Converts standard Clojure types to corresponding literals"))

(extend-protocol RDFConverter
  java.util.Date
  (convert [v] (let [g (GregorianCalendar. )
                     _ (.setTime g v)
                     x (-> (DatatypeFactory/newInstance)
                           (.newXMLGregorianCalendar g))]
                 (CalendarLiteralImpl. x)))
  java.net.URI
  (convert [v] (URIImpl. (.toString v)))
  Integer
  (convert [v] (NumericLiteralImpl. v))
  String
  (convert [v] (LiteralImpl. v)))

(defprotocol ClojureConverter
  (standardize [v] "Standardizes a value into something Idiomatic for Clojure"))

(extend-protocol ClojureConverter
  org.openrdf.model.URI
  (standardize [v] (java.net.URI. (str v)))
  Literal
  (standardize [v] (typed-value v))
  BNode
  (standardize [v] (keyword "_" (str "b" (.getID v)))))

(defn as-uri
  "Create a URI from a String"
  ^String
  [input]
  (java.net.URI. input))

