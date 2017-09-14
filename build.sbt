name := "summaryDb"

version := "0.1"

scalaVersion := "2.11.11"

libraryDependencies += "com.typesafe.slick" % "slick_2.11" % "3.2.1"
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.20.0"
libraryDependencies += "com.typesafe.play" % "play_2.11" % "2.6.3"
libraryDependencies += "org.yaml" % "snakeyaml" % "1.18"
libraryDependencies += "log4j" % "log4j" % "1.2.17"
libraryDependencies += "com.h2database" % "h2" % "1.4.196"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % Test
libraryDependencies += "org.testng" % "testng" % "6.8" % Test

