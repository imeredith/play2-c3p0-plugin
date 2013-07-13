name := "play2-c3p0-plugin"

version := "0.2-SNAPSHOT"

scalaVersion := "2.10.0"

resolvers += "Typesafe releases" at "https://repo.typesafe.com/typesafe/releases"

libraryDependencies ++= Seq(
	 "com.mchange" % "c3p0" % "0.9.2-pre5",
	 "play" % "play-jdbc_2.10" % "2.1.2"
)
