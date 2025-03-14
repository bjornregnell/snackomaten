ThisBuild / organization := "se.bjornregnell"
ThisBuild / version      := "0.1.1-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.4"

fork := true
run / javaOptions += "-Xmx8G"
outputStrategy := Some(StdoutOutput)
run / connectInput := true

lazy val root = (project in file("."))
  .aggregate(common, client, server)
  .settings(
    //update / aggregate := false
  )

lazy val common = (project in file("common"))
  .settings(
    // other settings
  )

lazy val client = (project in file("client"))
  .settings(
    // other settings
  )
  .dependsOn(common)

lazy val server = (project in file("server"))
  .settings(
    name := "SpamvasServer",
    scalacOptions := Seq("-encoding", "utf8", "-deprecation", "-unchecked", "-Werror", "-feature"),

    assembly / assemblyJarName := "SpamvasServer.jar",
    assembly / mainClass := Some("spamvas.ServerMain"),
  )
  .dependsOn(common)

