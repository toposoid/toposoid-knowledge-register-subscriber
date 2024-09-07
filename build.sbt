import Dependencies._

ThisBuild / scalaVersion     := "2.13.11"
ThisBuild / version          := "0.6-SNAPSHOT"
ThisBuild / organization     := "com.ideal.linked"
ThisBuild / organizationName := "toposoid-knowledge-register-subscriber"

lazy val root = (project in file("."))
  .settings(
    name := "toposoid-knowledge-register-subscriber",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.ideal.linked" %% "toposoid-sentence-transformer-neo4j" % "0.6-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-feature-vectorizer" % "0.6-SNAPSHOT",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.13",
    libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "2.0.2",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.2",
    libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1",

  )

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
