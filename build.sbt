name := "subfinder"
version := "2.0.0-SNAPSHOT"
organization := "tv.nabo"

scalaVersion := "2.13.12"

fork := true
outputStrategy := Some(StdoutOutput)

assembly / mainClass := Some("tv.nabo.subfinder.Subfinder")
assembly / assemblyJarName := "subfinder.jar"
assemblyMergeStrategy := {
  case x if Assembly.isConfigFile(x) =>
    MergeStrategy.concat
  case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
    MergeStrategy.rename
  case PathList("META-INF", xs @ _*) =>
    (xs map {_.toLowerCase}) match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
        MergeStrategy.discard
      case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
        MergeStrategy.discard
      case "plexus" :: xs =>
        MergeStrategy.discard
      case "services" :: xs =>
        MergeStrategy.filterDistinctLines
      case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
        MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.first
    }
  case _ => MergeStrategy.first
}

libraryDependencies ++= Seq(
  "com.outr" %% "spice-client-okhttp" % "0.5.9"
)