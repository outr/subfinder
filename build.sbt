name := "SubtitleFinder"
version := "1.0.0"
organization := "tv.nabo"

scalaVersion := "2.11.8"
sbtVersion := "0.13.13"

fork := true
javaOptions += "-Djava.net.preferIPv4Stack=true"

libraryDependencies += "org.apache.xmlrpc" % "xmlrpc-client" % "3.1.3"
libraryDependencies += "org.powerscala" %% "powerscala-io" % "2.0.2"
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
libraryDependencies += "com.eed3si9n" %% "gigahorse-asynchttpclient" % "0.2.0"