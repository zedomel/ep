name := """petricaep"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.7"

resolvers += "ICM repository" at "http://maven.icm.edu.pl/artifactory/repo"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  "org.neo4j" % "neo4j-jdbc-bolt" % "3.0.1",
  "org.apache.lucene" % "lucene-core" % "5.3.1",
  "org.apache.lucene" % "lucene-spellchecker" % "3.6.2",
  "org.apache.tika" % "tika-app" % "1.13",
  "io.malcolmgreaves" % "cybozu-language-detection_2.10" % "1.1.1",
  "com.yuzeh" % "crfpp-parser_2.9.3" % "1.0.2",
  "commons-pool" % "commons-pool" % "1.6",
  "commons-io" % "commons-io" % "2.5",
  "commons-logging" % "commons-logging" % "1.2",
  "org.apache.commons" % "commons-lang3" % "3.4",
  "log4j" % "log4j" % "1.2.17",
  "com.cybozu.labs" % "langdetect" % "1.1-20120112",
  "xerces" % "xercesImpl" % "2.11.0",
  "directory-naming" % "naming-java" % "0.8",
  "net.arnx" % "jsonic" % "1.3.5",
  "org.apache.pdfbox" % "pdfbox" % "2.0.3",
  "net.sourceforge.saxon"  % "saxon" % "9.1.0.8",
  "xom" % "xom" % "1.2.5",
  "org.carrot2" % "carrot2-core" % "3.14.0",
  "pl.edu.icm.cermine" % "cermine-impl" % "1.9",
  "colt" % "colt" % "1.2.0",
  "org.apache.httpcomponents" % "httpcore" % "4.4.5",
  "org.apache.httpcomponents" % "httpclient" % "4.5.2"	
//  "org.jbox2d" % "jbox2d" % "2.2.1.1",
//  "org.jbox2d" % "jbox2d-serializaton" % "1.1.0"
)

//dependencyOverrides += "org.springframework" % "spring-context" % "3.1.0"
dependencyOverrides += "org.apache.lucene" % "lucene-core" % "5.3.1"
//dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.4" 
//dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.8.4"
//dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-annotations" % "2.8.4"

//dependencyOverrides += "org.apache.lucene" % "lucene-backward-codecs" % "6.2.0"
//dependencyOverrides += "org.apache.lucene" % "lucene-codecs" % "6.2.0"
//dependencyOverrides += "org.apache.lucene" % "lucene-queryparser" % "6.2.0"
