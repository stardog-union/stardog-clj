 ; Copyright (C) 2014 Clark & Parsia
 ; Copyright (C) 2014 Paul Gearon
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

(defproject stardog-clj "3.0.0"
  :description "Stardog-clj: Clojure bindings for Stardog"
  :url "http://stardog.com"
  :license {:name "Apache License"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.complexible.stardog.search.http/stardog-search-protocols-http-client "3.0"]
                 [com.complexible.stardog.reasoning.http/stardog-reasoning-protocols-http-client "3.0"]
                 [com.complexible.stardog.versioning.http/stardog-versioning-protocols-http-client "3.0"]
                 [com.complexible.stardog.protocols.http/stardog-protocols-http-client "3.0"]
                 [com.complexible.common/cp-common-utils "4.0"]
                 [com.complexible.stardog.icv.http/stardog-icv-protocols-http-client "3.0"]
                 [com.complexible.stardog/stardog-api "3.0"]
                 [org.openrdf.sesame/sesame "2.7.14"]

		]
  :repositories [["stardog" "http://maven.stardog.com"]]
  :plugins [[jonase/eastwood "0.0.2"]
            [lein-midje "3.1.3"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                  :plugins [[lein-midje "3.1.3"]]}} )
