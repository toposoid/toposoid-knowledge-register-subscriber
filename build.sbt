import Dependencies._
import de.heikoseeberger.sbtheader.License

ThisBuild / scalaVersion     := "3.3.6"
ThisBuild / version          := "0.7-SNAPSHOT"
ThisBuild / organization     := "com.ideal.linked"
//ThisBuild / organizationName := "toposoid-knowledge-register-subscriber"

lazy val root = (project in file("."))
  .settings(
    name := "toposoid-knowledge-register-subscriber",
    resolvers += Resolver.mavenLocal,
    mainClass := Some("com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber"),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.ideal.linked" %% "toposoid-sentence-transformer-neo4j" % "0.7-SNAPSHOT" exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.ideal.linked" %% "toposoid-feature-vectorizer" % "0.7-SNAPSHOT" exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.10.9" exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.typesafe.akka" %% "akka-pki" % "2.10.9" exclude("org.slf4j","slf4j-api"),    
    libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.7.2" exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "9.0.2" exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.10.9" exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "org.playframework" %% "play-json" % "3.0.6" exclude("org.slf4j","slf4j-api"),     
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.36"
    //libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1",

  )

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("AGPL-3.0-or-later", new URL("http://www.gnu.org/licenses/agpl-3.0.en.html"))
headerLicense := Some(License.AGPLv3("2025", organizationName.value))
