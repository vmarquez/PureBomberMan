name := "puregame"

version := ".1-SNAPSHOT"

scalaVersion := "2.10.0"

resolvers ++= Seq("Sonatype Nexus releases" at "https://oss.sonatype.org/content/repositories/releases",
                 "Sonatype Nexus snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
                "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies +=  "org.scalaz" % "scalaz-effect_2.10" % "7.0.0"

libraryDependencies += "org.scalaz" % "scalaz-concurrent_2.10" % "7.0.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "io.argonaut" %% "argonaut" % "6.0-RC3"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.10.1" % "test"

scalariformSettings
