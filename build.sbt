import Dependencies._

ThisBuild / scalaVersion     := "2.13.11"
ThisBuild / version          := "0.6-SNAPSHOT"
ThisBuild / organization     := "com.ideal.linked"
ThisBuild / organizationName := "toposoid-knowledge-register-subscriber"

lazy val root = (project in file("."))
  .settings(
    name := "toposoid-knowledge-register-subscriber",
      libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.6-SNAPSHOT",
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


// Uncomment the following for publishing to Sonatype.
// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for more detail.

// ThisBuild / description := "Some descripiton about your project."
// ThisBuild / licenses    := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
// ThisBuild / homepage    := Some(url("https://github.com/example/project"))
// ThisBuild / scmInfo := Some(
//   ScmInfo(
//     url("https://github.com/your-account/your-project"),
//     "scm:git@github.com:your-account/your-project.git"
//   )
// )
// ThisBuild / developers := List(
//   Developer(
//     id    = "Your identifier",
//     name  = "Your Name",
//     email = "your@email",
//     url   = url("http://your.url")
//   )
// )
// ThisBuild / pomIncludeRepository := { _ => false }
// ThisBuild / publishTo := {
//   val nexus = "https://oss.sonatype.org/"
//   if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
//   else Some("releases" at nexus + "service/local/staging/deploy/maven2")
// }
// ThisBuild / publishMavenStyle := true
