import play.sbt.PlayImport._

name := "amazon-wordcloud"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  ws,
  "net.ruippeixotog"    %%  "scala-scraper"     % "0.1.2",
  "org.apache.spark"    %%  "spark-core"        % "1.5.1",
  "org.apache.spark"    %%  "spark-streaming"   % "1.5.1",
  "com.twitter"         %%  "algebird-core"     % "0.11.0",
  "com.twitter"         %%  "storehaus-core"    % "0.12.0",
  "com.twitter"         %%  "storehaus-redis"   % "0.12.0",
  "org.apache.opennlp"  %   "opennlp-tools"     % "1.6.0",
  "org.scalatest"       %%  "scalatest"         % "2.2.1"     % "test",
  "org.scalatestplus"   %%  "play"              % "1.4.0-M3"  % "test"
)

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
)

resolvers ++= Seq(
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

routesGenerator := InjectedRoutesGenerator

scalariformSettings
