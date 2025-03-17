lazy val snackomatenVersion = "0.1.0"

ThisBuild / organization := "se.bjornregnell"
ThisBuild / version      := snackomatenVersion
ThisBuild / scalaVersion := "3.6.4"

Global / onChangedBuildSource := IgnoreSourceChanges

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
    name := "snackomatenClient",
    scalacOptions := Seq("-encoding", "utf8", "-deprecation", "-unchecked", "-Werror", "-feature"),

    assembly / assemblyJarName := s"snackomatenClient-assembly-$snackomatenVersion.jar",
    assembly / mainClass := Some("snackomaten.ClientMain"),
  )
  .dependsOn(common)

lazy val server = (project in file("server"))
  .settings(
    name := "snackomatenServer",
    scalacOptions := Seq("-encoding", "utf8", "-deprecation", "-unchecked", "-Werror", "-feature"),

    assembly / assemblyJarName := s"snackomatenServer-assembly-$snackomatenVersion.jar",
    assembly / mainClass := Some("snackomaten.ServerMain"),
  )
  .dependsOn(common)

