
name := "scorex-core"

lazy val commonSettings = Seq(
  resolvers += Resolver.sonatypeRepo("public"),
  wartremoverErrors ++= Seq(
    Wart.Recursion,
    Wart.TraversableOps,
    Wart.Null,
    Wart.Product,
    Wart.PublicInference,
    Wart.FinalVal,
    Wart.IsInstanceOf,
    Wart.JavaConversions,
    Wart.JavaSerializable,
    Wart.Serializable,
    Wart.OptionPartial),
  scalaVersion := "2.12.7",
  organization := "org.scorexfoundation",
  licenses := Seq("CC0" -> url("https://creativecommons.org/publicdomain/zero/1.0/legalcode")),
  homepage := Some(url("https://github.com/ScorexFoundation/Scorex")),
  pomExtra :=
    <scm>
      <url>git@github.com:ScorexFoundation/Scorex.git</url>
      <connection>scm:git:git@github.com:ScorexFoundation/Scorex.git</connection>
    </scm>
      <developers>
        <developer>
          <id>kushti</id>
          <name>Alexander Chepurnoy</name>
          <url>http://chepurnoy.org/</url>
        </developer>
      </developers>
)

enablePlugins(GitVersioning)

version in ThisBuild := {
  if (git.gitCurrentTags.value.nonEmpty) {
    git.gitDescribedVersion.value.get
  } else {
    if (git.gitHeadCommit.value.contains(git.gitCurrentBranch.value)) {
      git.gitHeadCommit.value.get.take(8) + "-SNAPSHOT"
    } else {
      git.gitCurrentBranch.value + "-" + git.gitHeadCommit.value.get.take(8) + "-SNAPSHOT"
    }
  }
}

git.gitUncommittedChanges in ThisBuild := true

scalaVersion := "2.12.3"
organization := "org.scorexfoundation"

val circeVersion = "0.8.0"

val networkDependencies = Seq(
  "com.typesafe.akka" % "akka-actor_2.12" % "2.5.24",
  "com.typesafe.akka" % "akka-http-core_2.12" % "10.1.9",
  "com.typesafe.akka" % "akka-http_2.12" % "10.1.9",
  "com.typesafe.akka" % "akka-parsing_2.12" % "10.1.9",
  "com.typesafe.akka" % "akka-protobuf_2.12" % "2.5.24",
  "com.typesafe.akka" % "akka-stream_2.12" % "2.5.24",
  "org.bitlet" % "weupnp" % "0.1.+",
  "commons-net" % "commons-net" % "3.+"
)

val apiDependencies = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "com.typesafe.akka" %% "akka-http" % "10.+",
  "de.heikoseeberger" %% "akka-http-circe" % "1.19.0"
)

val loggingDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.+"
)

val testingDependencies = Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.5.+" % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.+" % "test",
  "org.scalactic" %% "scalactic" % "3.0.3" % "test",
  "org.scalatest" %% "scalatest" % "3.0.3" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.+"
)

libraryDependencies ++= Seq(
  "com.iheart" %% "ficus" % "1.4.2",
  "org.scorexfoundation" %% "scrypto" % "2.+",
  "org.scorexfoundation" %% "scorex-util" % "0.1.3",
) ++ networkDependencies ++ apiDependencies ++ loggingDependencies ++ testingDependencies


scalacOptions ++= Seq("-Xfatal-warnings", "-feature", "-deprecation")

javaOptions ++= Seq(
  "-server"
)

testOptions in Test += Tests.Argument("-oD", "-u", "target/test-reports")

//publishing settings

publishMavenStyle := true

publishArtifact in Test := false

fork := true

pomIncludeRepository := { _ => false }

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

lazy val testkit = Project(id = "testkit", base = file(s"testkit"))
  .dependsOn(basics)
  .settings(commonSettings: _*)

lazy val examples = Project(id = "examples", base = file(s"examples"))
  .dependsOn(basics, testkit)
  .settings(commonSettings: _*)

lazy val basics = Project(id = "scorex", base = file("."))
  .settings(commonSettings: _*)

//publishing settings

licenses in ThisBuild := Seq("CC0" -> url("https://creativecommons.org/publicdomain/zero/1.0/legalcode"))

homepage in ThisBuild := Some(url("https://github.com/ScorexFoundation/Scorex"))

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq

fork in ThisBuild := true

pomIncludeRepository in ThisBuild := { _ => false }

licenses in ThisBuild := Seq("CC0" -> url("https://creativecommons.org/publicdomain/zero/1.0/legalcode"))

homepage in ThisBuild := Some(url("https://github.com/ScorexFoundation/Scorex"))

pomExtra in ThisBuild :=
  <scm>
    <url>git@github.com:ScorexFoundation/Scorex.git</url>
    <connection>scm:git@github.com:ScorexFoundation/Scorex.git</connection>
  </scm>
    <developers>
      <developer>
        <id>kushti</id>
        <name>Alexander Chepurnoy</name>
        <url>http://chepurnoy.org/</url>
      </developer>
      <developer>
        <id>catena2w</id>
        <name>catena</name>
        <url>https://github.com/catena2w</url>
      </developer>
    </developers>


//FindBugs settings

findbugsReportType := Some(FindbugsReport.PlainHtml)
