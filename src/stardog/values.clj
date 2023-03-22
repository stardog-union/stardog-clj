;; Copyright (C) 2014 Paula Gearon
;; Copyright (C) 2014 Clark & Parsia
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

(ns stardog.values
  (:import [com.stardog.stark IRI Literal BNode Values Datatype]
           [com.stardog.stark.impl CalendarLiteral IRIImpl TypedLiteral BooleanLiteral LanguageLiteral]
           [java.util Date GregorianCalendar UUID Map]
           [javax.xml.datatype DatatypeConfigurationException DatatypeFactory XMLGregorianCalendar]))


(defmulti typed-value (fn [^Literal v] (.. v datatype toString)))

(defmethod typed-value "http://www.w3.org/2001/XMLSchema#integer"
  [^Literal v] (Literal/intValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#int"
  [^Literal v] (Literal/intValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#boolean"
  [^Literal v] (Literal/booleanValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#byte"
  [^Literal v] (Literal/byteValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#dateTime"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#time"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#date"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gYearMonth"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gMonthYear"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gYear"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gMonth"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gDay"
  [^Literal v] (.. v calendarValue toGregorianCalendar getTime))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#decimal"
  [^Literal v] (Literal/decimalValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#double"
  [^Literal v] (Literal/doubleValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#float"
  [^Literal v] (Literal/floatValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#long"
  [^Literal v] (Literal/longValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#short"
  [^Literal v] (Literal/shortValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#string"
  [^Literal v] (.label v))
(defmethod typed-value :default
  [^Literal v]
  (let [label (.label v)]
    (if-let [lang (.lang v)]
      {:lang lang :value label}
      (if-let [dt (.datatype v)]
        {:datatype dt :value label}
        label))))

(defprotocol URIType
  (to-uri [u] "Converts to an internal URI type"))

(extend-protocol URIType
  nil
  (to-uri [u] u)
  String
  (to-uri [u] (IRIImpl. u))
  java.net.URI
  (to-uri [u] (IRIImpl. (str u)))
  java.net.URL
  (to-uri [u] (IRIImpl. (str u)))
  IRI
  (to-uri [u] u))

(defprotocol RDFConverter
  (convert [v] "Converts standard Clojure types to corresponding literals"))

(extend-protocol RDFConverter
  nil
  (convert [v] v)
  java.util.Date
  (convert [v] (let [g (doto (GregorianCalendar.) (.setTime v))]
                 (-> (DatatypeFactory/newInstance)
                     (.newXMLGregorianCalendar g)
                     (Values/literal))))
  GregorianCalendar
  (convert [v] (-> (DatatypeFactory/newInstance)
                   (.newXMLGregorianCalendar v)
                   (Values/literal)))
  java.net.URI
  (convert [v] (to-uri v))
  java.net.URL
  (convert [v] (to-uri v))
  UUID
  (convert [v] (to-uri (str "urn:uuid:" v)))
  Boolean
  (convert [v] (if v BooleanLiteral/TRUE BooleanLiteral/FALSE))
  Byte
  (convert [v] (Values/literal (byte v)))
  Short
  (convert [v] (Values/literal (short v)))
  Float
  (convert [v] (Values/literal (float v)))
  Double
  (convert [v] (Values/literal (double v)))
  Integer
  (convert [v] (Values/literal (int v)))
  Long
  (convert [v] (Values/literal (long v)))
  BigInteger
  (convert [v] (Values/literal (biginteger v)))
  BigDecimal
  (convert [v] (Values/literal (bigdec v)))
  String
  (convert [v] (Values/literal v))
  Map
  (convert [{:keys [^String value ^String lang datatype]}]
    (cond lang     (LanguageLiteral. value lang)
          datatype (TypedLiteral. value (Datatype/of (to-uri datatype)))
          :default (TypedLiteral. value Datatype/UDF))))

(defprotocol ClojureConverter
  (standardize [v] "Standardizes a value into something Idiomatic for Clojure"))

(extend-protocol ClojureConverter
  IRI
  (standardize [v] (let [u (str v)]
                     (if (.startsWith u "urn:uuid:")
                       (UUID/fromString (subs u 9))
                       (java.net.URI. u))))
  Literal
  (standardize [v] (typed-value v))
  BNode
  (standardize [v] (keyword "_" (str "b" (.id v))))

  CalendarLiteral
  (standardize [v]
    (.getTime (.toGregorianCalendar (.unwrap v)))))

(defn as-uri
  "Create a URI from a String"
  ^String
  [input]
  (java.net.URI. input))
