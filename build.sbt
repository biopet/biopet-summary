organization := "com.github.biopet"
name := "biopet-summary-utils"

scalaVersion := "2.11.11"

libraryDependencies += "com.typesafe.slick" %% "slick" % "3.2.1"
libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.6.4"

libraryDependencies += "com.github.biopet" %% "biopet-common-utils" % "0.1.0-SNAPSHOT"
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.20.0"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.3"
libraryDependencies += "com.h2database" % "h2" % "1.4.196"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % Test
libraryDependencies += "org.testng" % "testng" % "6.8" % Test

useGpg := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
