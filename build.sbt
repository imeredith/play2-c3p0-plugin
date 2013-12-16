name := "play2-c3p0-plugin"

version := "0.3-SNAPSHOT"

scalaVersion := "2.10.2"

resolvers += "Typesafe releases" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
	 "com.mchange" % "c3p0" % "0.9.5-pre5",
	 "com.typesafe.play" % "play-jdbc_2.10" % "2.2.1"
)
